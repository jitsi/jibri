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

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.JibriService
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
import org.jitsi.jibri.statsd.ASPECT_START
import org.jitsi.jibri.statsd.ASPECT_STOP
import org.jitsi.jibri.statsd.JibriStatsDClient
import org.jitsi.jibri.statsd.TAG_SERVICE_LIVE_STREAM
import org.jitsi.jibri.statsd.TAG_SERVICE_RECORDING
import org.jitsi.jibri.statsd.TAG_SERVICE_SIP_GATEWAY
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.schedule
import org.jitsi.metaconfig.config
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class JibriBusyException : Exception()

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
 *
 * TODO: we mark 'Any' as the status type we publish because we have 2 different status types we want to publish:
 * ComponentBusyStatus and ComponentState and i was unable to think of a better solution for that (yet...)
 */
class JibriManager : StatusPublisher<Any>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private var currentActiveService: JibriService? = null
    /**
     * Store some arbitrary context optionally sent in the start service request so that we can report it in our
     * status
     */
    var currentEnvironmentContext: EnvironmentContext? = null
    /**
     * A function which will be executed the next time this Jibri is idle.  This can be used to schedule work that
     * can't be run while a Jibri session is active
     */
    private var pendingIdleFunc: () -> Unit = {}
    private var serviceTimeoutTask: ScheduledFuture<*>? = null

    private val enableStatsD: Boolean by config {
        "JibriConfig::enableStatsD" { Config.legacyConfigSource.enabledStatsD!! }
        "jibri.stats.enable-stats-d".from(Config.configSource)
    }

    private val singleUseMode: Boolean by config {
        "JibriConfig::singleUseMode" { Config.legacyConfigSource.singleUseMode!! }
        "jibri.single-use-mode".from(Config.configSource)
    }

    private val statsDClient: JibriStatsDClient? = if (enableStatsD) { JibriStatsDClient() } else null

    /**
     * Note: should only be called if the instance-wide lock is held (i.e. called from
     * one of the synchronized methods)
     * TODO: instead of the synchronized decorators, use a synchronized(this) block
     * which we can also use here
     */
    private fun throwIfBusy() {
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            statsDClient?.incrementCounter(ASPECT_BUSY, TAG_SERVICE_RECORDING)
            throw JibriBusyException()
        }
    }

    /**
     * Starts a [FileRecordingJibriService] to record the call described
     * in the params to a file.
     */
    @Synchronized
    fun startFileRecording(
        serviceParams: ServiceParams,
        fileRecordingRequestParams: FileRecordingRequestParams,
        environmentContext: EnvironmentContext? = null,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ) {
        throwIfBusy()
        logger.info("Starting a file recording with params: $fileRecordingRequestParams")
        val service = FileRecordingJibriService(
            FileRecordingParams(
                fileRecordingRequestParams.callParams,
                fileRecordingRequestParams.sessionId,
                fileRecordingRequestParams.callLoginParams,
                serviceParams.appData?.fileRecordingMetadata
            )
        )
        statsDClient?.incrementCounter(ASPECT_START, TAG_SERVICE_RECORDING)
        startService(service, serviceParams, environmentContext, serviceStatusHandler)
    }

    /**
     * Starts a [StreamingJibriService] to capture the call according
     * to [streamingParams].
     */
    @Synchronized
    fun startStreaming(
        serviceParams: ServiceParams,
        streamingParams: StreamingParams,
        environmentContext: EnvironmentContext? = null,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ) {
        logger.info("Starting a stream with params: $serviceParams $streamingParams")
        throwIfBusy()
        val service = StreamingJibriService(streamingParams)
        statsDClient?.incrementCounter(ASPECT_START, TAG_SERVICE_LIVE_STREAM)
        startService(service, serviceParams, environmentContext, serviceStatusHandler)
    }

    @Synchronized
    fun startSipGateway(
        serviceParams: ServiceParams,
        sipGatewayServiceParams: SipGatewayServiceParams,
        environmentContext: EnvironmentContext? = null,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ) {
        logger.info("Starting a SIP gateway with params: $serviceParams $sipGatewayServiceParams")
        throwIfBusy()
        val service = SipGatewayJibriService(SipGatewayServiceParams(
            sipGatewayServiceParams.callParams,
            sipGatewayServiceParams.callLoginParams,
            sipGatewayServiceParams.sipClientParams
        ))
        statsDClient?.incrementCounter(ASPECT_START, TAG_SERVICE_SIP_GATEWAY)
        return startService(service, serviceParams, environmentContext, serviceStatusHandler)
    }

    /**
     * Helper method to handle the boilerplate of starting a [JibriService].
     */
    private fun startService(
        jibriService: JibriService,
        serviceParams: ServiceParams,
        environmentContext: EnvironmentContext?,
        serviceStatusHandler: JibriServiceStatusHandler? = null
    ) {
        publishStatus(ComponentBusyStatus.BUSY)
        if (serviceStatusHandler != null) {
            jibriService.addStatusHandler(serviceStatusHandler)
        }
        // The manager adds its own status handler so that it can stop
        // the error'd service and update presence appropriately
        jibriService.addStatusHandler {
            when (it) {
                is ComponentState.Error -> {
                    if (it.error.scope == ErrorScope.SYSTEM) {
                        statsDClient?.incrementCounter(ASPECT_ERROR, JibriStatsDClient.getTagForService(jibriService))
                        publishStatus(ComponentHealthStatus.UNHEALTHY)
                    }
                    stopService()
                }
                is ComponentState.Finished -> {
                    // If a 'stop' was received externally, then this stopService call
                    // will be redundant, but we need to make it anyway as the service
                    // can also signal that it has finished (based on its own checks)
                    // and needs to be stopped (cleaned up)
                    stopService()
                }
                else -> { /* No op */ }
            }
        }

        currentActiveService = jibriService
        currentEnvironmentContext = environmentContext
        if (serviceParams.usageTimeoutMinutes != 0) {
            logger.info("This service will have a usage timeout of ${serviceParams.usageTimeoutMinutes} minute(s)")
            serviceTimeoutTask =
                TaskPools.recurringTasksPool.schedule(serviceParams.usageTimeoutMinutes.toLong(), TimeUnit.MINUTES) {
                    logger.info("The usage timeout has elapsed, stopping the currently active service")
                    try {
                        stopService()
                    } catch (t: Throwable) {
                        logger.error("Error while stopping service due to usage timeout: $t")
                    }
                }
        }
        TaskPools.ioPool.submit {
            jibriService.start()
        }
    }

    /**
     * Stop the currently active [JibriService], if there is one
     */
    @Synchronized
    fun stopService() {
        val currentService = currentActiveService ?: run {
            // After an initial call to 'stopService', we'll stop ffmpeg and it will transition
            // to 'finished', causing the entire service to transition to 'finished' and trigger
            // another call to stopService (see the note above when installing the status handler
            // on the jibri service).  A more complete fix for this is much larger, so for now
            // we'll just check if the currentActiveService has already been cleared to prevent
            // doing a double stop (which is mostly harmless, but does fire an extra 'stop'
            // statsd event with an empty service tag)
            logger.info("No service active, ignoring stop")
            return
        }
        statsDClient?.incrementCounter(ASPECT_STOP, JibriStatsDClient.getTagForService(currentService))
        logger.info("Stopping the current service")
        serviceTimeoutTask?.cancel(false)
        // Note that this will block until the service is completely stopped
        currentService.stop()
        currentActiveService = null
        currentEnvironmentContext = null
        // Invoke the function we've been told to next time we're idle
        // and reset it
        pendingIdleFunc()
        pendingIdleFunc = {}
        if (singleUseMode) {
            logger.info("Jibri is in single-use mode, not returning to IDLE")
            publishStatus(ComponentBusyStatus.EXPIRED)
        } else {
            publishStatus(ComponentBusyStatus.IDLE)
        }
    }

    /**
    * Returns whether or not this Jibri is currently "busy".   "Busy" is
    * is defined as "does not currently have the capacity to spin up another
    * service"
    */
    @Synchronized
    fun busy(): Boolean = currentActiveService != null

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
