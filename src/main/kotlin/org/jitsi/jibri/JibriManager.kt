/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
 *
 */

package org.jitsi.jibri

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriStatusPacketExt
import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.FileRecordingJibriService
import org.jitsi.jibri.service.impl.FileRecordingParams
import org.jitsi.jibri.service.impl.SipGatewayJibriService
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingJibriService
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.statsd.ASPECT_BUSY
import org.jitsi.jibri.statsd.ASPECT_ERROR
import org.jitsi.jibri.statsd.TAG_SERVICE_LIVE_STREAM
import org.jitsi.jibri.statsd.TAG_SERVICE_RECORDING
import org.jitsi.jibri.statsd.TAG_SERVICE_SIP_GATEWAY
import org.jitsi.jibri.statsd.ASPECT_START
import org.jitsi.jibri.statsd.ASPECT_STOP
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.statsd.JibriStatsDClient
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.schedule
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

enum class StartServiceResult {
    SUCCESS,
    BUSY,
    ERROR
}

/**
 * Some of the values in [FileRecordingParams] come from the configuration
 * file, so the incoming request won't contain all of them.  This class
 * models the subset of values which will come in the request.
 */
data class FileRecordingRequestParams(
    /**
     * Which call we'll join
     */
    val callParams: CallParams,
    /**
     * The ID of this session
     */
    val sessionId: String,
    /**
     * The login information needed to appear invisible in
     * the call
     */
    val callLoginParams: XmppCredentials
)

/**
 * [JibriManager] is responsible for managing the various services Jibri
 * provides, as well as providing an API to query the health of this Jibri
 * instance.  NOTE: currently Jibri only runs a single service at a time, so
 * if one is running, the Jibri will describe itself as busy
 */
class JibriManager(
    private val config: JibriConfig,
    private val fileSystem: FileSystem = FileSystems.getDefault(),
    private val statsDClient: JibriStatsDClient? = null
) : StatusPublisher<JibriStatusPacketExt.Status>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private var currentActiveService: JibriService? = null
    private var currentEnvironmentContext: EnvironmentContext? = null
    private var pendingIdleFunc: () -> Unit = {}
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("JibriManager"))
    private var serviceTimeoutTask: ScheduledFuture<*>? = null

    /**
     * Starts a [FileRecordingJibriService] to record the call described
     * in the params to a file.  Returns a [StartServiceResult] to denote
     * whether the service was started successfully or not.
     */
    @Synchronized
    fun startFileRecording(
        serviceParams: ServiceParams,
        fileRecordingRequestParams: FileRecordingRequestParams,
        environmentContext: EnvironmentContext? = null,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ): StartServiceResult {
        logger.info("Starting a file recording with params: $fileRecordingRequestParams " +
                "finalize script path: ${config.finalizeRecordingScriptPath} and " +
                "recordings directory: ${config.recordingDirectory}")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            statsDClient?.incrementCounter(ASPECT_BUSY, TAG_SERVICE_RECORDING)
            return StartServiceResult.BUSY
        }
        val service = FileRecordingJibriService(
            FileRecordingParams(
                fileRecordingRequestParams.callParams,
                fileRecordingRequestParams.sessionId,
                fileRecordingRequestParams.callLoginParams,
                fileSystem.getPath(config.finalizeRecordingScriptPath),
                fileSystem.getPath(config.recordingDirectory)
            ),
            Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("FileRecordingJibriService"))
        )
        statsDClient?.incrementCounter(ASPECT_START, TAG_SERVICE_RECORDING)
        return startService(service, serviceParams, environmentContext, serviceStatusHandler)
    }

    /**
     * Starts a [StreamingJibriService] to capture the call according
     * to [streamingParams].  Returns a [StartServiceResult] to
     * denote whether the service was started successfully or not.
     */
    @Synchronized
    fun startStreaming(
        serviceParams: ServiceParams,
        streamingParams: StreamingParams,
        environmentContext: EnvironmentContext? = null,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ): StartServiceResult {
        logger.info("Starting a stream with params: $serviceParams $streamingParams")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            statsDClient?.incrementCounter(ASPECT_BUSY, TAG_SERVICE_LIVE_STREAM)
            return StartServiceResult.BUSY
        }
        val service = StreamingJibriService(streamingParams)
        statsDClient?.incrementCounter(ASPECT_START, TAG_SERVICE_LIVE_STREAM)
        return startService(service, serviceParams, environmentContext, serviceStatusHandler)
    }

    @Synchronized
    fun startSipGateway(
        serviceParams: ServiceParams,
        sipGatewayServiceParams: SipGatewayServiceParams,
        environmentContext: EnvironmentContext? = null,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ): StartServiceResult {
        logger.info("Starting a SIP gateway with params: $serviceParams $sipGatewayServiceParams")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            statsDClient?.incrementCounter(ASPECT_BUSY, TAG_SERVICE_SIP_GATEWAY)
            return StartServiceResult.BUSY
        }
        val service = SipGatewayJibriService(SipGatewayServiceParams(
            sipGatewayServiceParams.callParams,
            sipGatewayServiceParams.sipClientParams
        ))
        statsDClient?.incrementCounter(ASPECT_START, TAG_SERVICE_SIP_GATEWAY)
        return startService(service, serviceParams, environmentContext, serviceStatusHandler)
    }

    /**
     * Helper method to handle the boilerplate of starting a [JibriService].
     * Returns a [StartServiceResult] to denote whether the service was
     * started successfully or not.
     */
    private fun startService(
        jibriService: JibriService,
        serviceParams: ServiceParams,
        environmentContext: EnvironmentContext?,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ): StartServiceResult {
        publishStatus(JibriStatusPacketExt.Status.BUSY)
        if (serviceStatusHandler != null) {
            jibriService.addStatusHandler(serviceStatusHandler)
        }
        // The manager adds its own status handler so that it can stop
        // the error'd service and update presence appropriately
        jibriService.addStatusHandler {
            when (it) {
                JibriServiceStatus.ERROR -> {
                    statsDClient?.incrementCounter(ASPECT_ERROR, JibriStatsDClient.getTagForService(jibriService))
                    stopService()
                }
                JibriServiceStatus.FINISHED -> {
                    stopService()
                }
            }
        }

        currentActiveService = jibriService
        currentEnvironmentContext = environmentContext
        if (serviceParams.usageTimeoutMinutes != 0) {
            logger.info("This service will have a usage timeout of ${serviceParams.usageTimeoutMinutes} minute(s)")
            serviceTimeoutTask = executor.schedule(serviceParams.usageTimeoutMinutes.toLong(), TimeUnit.MINUTES) {
                logger.info("The usage timeout has elapsed, stopping the currently active service")
                try {
                    stopService()
                } catch (t: Throwable) {
                    logger.error("Error while stopping service due to usage timeout: $t")
                }
            }
        }
        if (!jibriService.start()) {
            stopService()
            statsDClient?.incrementCounter(ASPECT_ERROR, JibriStatsDClient.getTagForService(jibriService))
            return StartServiceResult.ERROR
        }
        return StartServiceResult.SUCCESS
    }

    /**
     * Stop the currently active [JibriService], if there is one
     */
    @Synchronized
    fun stopService() {
        statsDClient?.incrementCounter(ASPECT_STOP, JibriStatsDClient.getTagForService(currentActiveService))
        logger.info("Stopping the current service")
        serviceTimeoutTask?.cancel(false)
        // Note that this will block until the service is completely stopped
        currentActiveService?.stop()
        currentActiveService = null
        currentEnvironmentContext = null
        // Invoke the function we've been told to next time we're idle
        // and reset it
        pendingIdleFunc()
        pendingIdleFunc = {}
        publishStatus(JibriStatusPacketExt.Status.IDLE)
    }

    /**
     * Returns an object describing the "health" of this Jibri
     */
    @Synchronized
    fun healthCheck(): JibriHealth {
        return JibriHealth(busy(), currentEnvironmentContext)
    }

    /**
    * Returns whether or not this Jibri is currently "busy".   "Busy" is
    * is defined as "does not currently have the capacity to spin up another
    * service"
    */
    @Synchronized
    private fun busy(): Boolean = currentActiveService != null

    /**
     * Execute the given function the next time Jibri is idle
     */
    @Synchronized
    fun executeWhenIdle(func: () -> Unit) {
        if (!busy()) {
            func()
        } else {
            pendingIdleFunc = func
        }
    }
}
