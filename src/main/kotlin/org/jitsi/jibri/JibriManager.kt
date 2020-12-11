/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.jibri

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.streaming.StreamingParams
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.statsd.JibriStatsDClient
import org.jitsi.jibri.statsd.liveStreamStarted
import org.jitsi.jibri.statsd.recordingStarted
import org.jitsi.jibri.statsd.requestDeniedBecauseBusy
import org.jitsi.jibri.statsd.sipGwStarted
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.utils.logging2.createLogger
import kotlin.time.Duration
import kotlin.time.minutes

/**
 * Arbitrary Jibri specific data that can be passed in the
 * [JibriIq#appData] field.  This entire structure will be parsed
 * from a JSON-encoded string (the [JibriIq#appData] field).
 */
data class AppData(
    /**
     * A JSON map representing arbitrary data to be written
     * to the metadata file when doing a recording.
     */
    @JsonProperty("file_recording_metadata")
    val fileRecordingMetadata: Map<Any, Any>?
)

/**
 * Parameters needed for starting any Jibri job.
 */
data class JobParams(
    val sessionId: String,
    val usageTimeout: Duration,
    val callParams: CallParams,
    val appData: AppData? = null
)

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class EnvironmentContext(
    val name: String
)

class JibriManager(
    private val jibriJobFactory: JobFactory = JobFactoryImpl
) {
    private val logger = createLogger()

    /**
     * We use this same scope for all jobs (each of which will throw when it finishes), so we use a SupervisorScope
     * to keep this scope valid even when children throw, but still allow cancelling any active jobs by cancelling
     * this scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + CoroutineName("JibriManager"))
    private val _currentState = MutableStateFlow<JibriState>(JibriState.Idle)
    private val singleUseMode: Boolean by config("jibri.single-use-mode".from(Config.configSource))
    private val enableStatsD: Boolean by config("jibri.stats.enable-stats-d".from(Config.configSource))

    /**
     * A function which will be executed the next time this Jibri is idle.  This can be used to schedule work that
     * can't be run while a Jibri session is active
     */
    private var pendingIdleFunc: () -> Unit = {}

    private val statsDClient: JibriStatsDClient? = if (enableStatsD) { JibriStatsDClient() } else null

    val currentState: StateFlow<JibriState> = _currentState.asStateFlow()

    private fun throwIfBusy() {
        if (currentState.value != JibriState.Idle) {
            statsDClient?.requestDeniedBecauseBusy()
            throw JibriBusy
        }
    }

    @Synchronized
    fun startFileRecordingSession(
        jobParams: JobParams,
        environmentContext: EnvironmentContext? = null
    ): JibriSession {
        throwIfBusy()
        logger.info("Starting recording session")
        val recording = tryCreateJob {
            jibriJobFactory.createRecordingJob(
                logger,
                jobParams.sessionId,
                jobParams.callParams,
                jobParams.appData?.fileRecordingMetadata
            )
        }
        logger.info("Recording job created")

        _currentState.value = JibriState.Busy(environmentContext)
        statsDClient?.recordingStarted()
        return runJobAsync(recording, jobParams.usageTimeout)
    }

    @Synchronized
    fun startStreamingSession(
        jobParams: JobParams,
        streamingParams: StreamingParams,
        environmentContext: EnvironmentContext? = null
    ): JibriSession {
        throwIfBusy()
        logger.info("Starting streaming session")
        val streaming = tryCreateJob {
            jibriJobFactory.createStreamingJob(
                logger,
                jobParams.sessionId,
                jobParams.callParams,
                streamingParams
            )
        }
        logger.info("Streaming job created")

        _currentState.value = JibriState.Busy(environmentContext)
        statsDClient?.liveStreamStarted()
        return runJobAsync(streaming, jobParams.usageTimeout)
    }

    @Synchronized
    fun startSipGwSession(
        jobParams: JobParams,
        sipClientParams: SipClientParams,
        environmentContext: EnvironmentContext? = null
    ): JibriSession {
        throwIfBusy()
        val sipgw = tryCreateJob {
            jibriJobFactory.createSipGwJob(
                logger,
                jobParams.sessionId,
                jobParams.callParams,
                sipClientParams
            )
        }

        _currentState.value = JibriState.Busy(environmentContext)
        statsDClient?.sipGwStarted()
        return runJobAsync(sipgw, jobParams.usageTimeout)
    }

    @Synchronized
    fun shutdown() {
        scope.cancel("Shutting down")
    }

    @Synchronized
    private fun jobFinished(error: Throwable?) {
        logger.info("Session done, Jibri no longer busy")
        pendingIdleFunc()
        pendingIdleFunc = {}
        when (error) {
            is JibriSystemError -> {
                logger.info("System error occurred, going to unhealthy state")
                _currentState.value = JibriState.Error(error)
            }
            else -> {
                if (error is JibriError) {
                    logger.error("Session had an error: ${error.message}")
                }
                _currentState.value = if (singleUseMode) JibriState.Expired else JibriState.Idle
            }
        }
    }

    @Synchronized
    fun executeWhenIdle(func: () -> Unit) {
        if (currentState.value !is JibriState.Busy) {
            func()
        } else {
            pendingIdleFunc = func
        }
    }

    /**
     * Run the given [job] with [timeout], if one is set.  Adds a handler to run [jobFinished] once the job completes.
     */
    private fun runJobAsync(job: JibriJob, timeout: Duration): JibriSession {
        val deferred = scope.async(CoroutineName(job.name)) {
            logger.info("Job ${job.name} launched")
            try {
                if (timeout != 0.minutes) {
                    logger.info("Starting job ${job.name} with a timeout of $timeout")
                    withTimeout(timeout) {
                        job.run()
                    }
                } else {
                    logger.info("Starting job ${job.name} without timeout")
                    job.run()
                }
            } catch (t: Throwable) {
                logger.info("Job ${job.name} finished (${t.message})")
                // Invoke jobFinished here, rather than using a CompletionHandler on the deferred, so that we can
                // guarantee it has run by the time the session (deferred) completes.  The only place I saw that was
                // an issue for this was the tests, but I think it's more correct, semantically, to ensure jobFinished
                // runs before the deferred completes.
                jobFinished(t)
                throw t
            }
        }
        return JibriSession(deferred, job.state)
    }

    /**
     * Try to run the given [block] to create a [JibriJob], updating [_currentState] if any error occurs
     */
    private fun tryCreateJob(block: () -> JibriJob): JibriJob {
        return try {
            block()
        } catch (j: JibriSystemError) {
            _currentState.value = JibriState.Error(j)
            throw j
        } catch (j: JibriRequestError) {
            // I don't think we need to go to expired here, as we never even started the job
            _currentState.value = JibriState.Idle
            throw j
        } catch (t: Throwable) {
            throw Exception("This should never hit!  All exceptions should be wrapped, but ${t.message} " +
                "made it through")
        }
    }
}
