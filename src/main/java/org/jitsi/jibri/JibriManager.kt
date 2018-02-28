package org.jitsi.jibri

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriStatusPacketExt
import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.impl.FileRecordingJibriService
import org.jitsi.jibri.service.impl.StreamingJibriService
import org.jitsi.jibri.service.impl.RecordingOptions
import org.jitsi.jibri.service.impl.StreamingOptions
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.schedule
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

enum class StartServiceResult {
    SUCCESS,
    BUSY,
    ERROR
}

/**
 * Parameters needed for joining the call in Selenium
 */
data class CallParams(
    val callUrlInfo: CallUrlInfo = CallUrlInfo(),
    val callLoginParams: XmppCredentials = XmppCredentials()
)

/**
 * Parameters needed for starting any [JibriService]
 */
data class ServiceParams(
    val usageTimeoutMinutes: Int
)

/**
 * Parameters needed for starting a [FileRecordingJibriService]
 */
data class FileRecordingParams(
    val callParams: CallParams
)

/**
 * Parameters needed for starting a [StreamingJibriService]
 */
data class StreamingParams(
    val callParams: CallParams,
    val youTubeStreamKey: String
)

/**
 * [JibriManager] is responsible for managing the various services Jibri
 * provides, as well as providing an API to query the health of this Jibri
 * instance.  NOTE: currently Jibri only runs a single service at a time, so
 * if one is running, the Jibri will describe itself as busy
 */
class JibriManager(private val configFile: File) : StatusPublisher<JibriStatusPacketExt.Status>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    //TODO: public so main can get to it and pass the xmpp stuff to the xmpp api,
    //  need to figure out a better way for that
    public lateinit var config: JibriConfig
    private var currentActiveService: JibriService? = null
    private var pendingIdleFunc: () -> Unit = {}
    val executor = Executors.newSingleThreadScheduledExecutor()
    var serviceTimeoutTask: ScheduledFuture<*>? = null

    init {
        loadConfig(configFile)
    }

    private fun loadConfig(configFile: File) {
        config = jacksonObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true).readValue(configFile)
        logger.info("Parsed config:\n$config")
    }

    /**
     * Starts a [FileRecordingJibriService] to record the call described
     * in the params to a file.  Returns a [StartServiceResult] to denote
     * whether the service was started successfully or not.
     */
    @Synchronized
    fun startFileRecording(
            serviceParams: ServiceParams,
            fileRecordingParams: FileRecordingParams,
            serviceStatusHandler: JibriServiceStatusHandler? = null): StartServiceResult {
        logger.info("Starting a file recording with params: $fileRecordingParams")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            return StartServiceResult.BUSY
        }
        val service = FileRecordingJibriService(RecordingOptions(
                recordingDirectory = File(config.recordingDirectory),
                callParams = fileRecordingParams.callParams,
                finalizeScriptPath = config.finalizeRecordingScriptPath
        ))
        return startService(service, serviceParams, serviceStatusHandler)
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
            serviceStatusHandler: JibriServiceStatusHandler? = null): StartServiceResult {
        logger.info("Starting a stream with params: $streamingParams")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            return StartServiceResult.BUSY
        }
        val service = StreamingJibriService(StreamingOptions(
                youTubeStreamKey = streamingParams.youTubeStreamKey,
                callParams = streamingParams.callParams
        ))
        return startService(service, serviceParams, serviceStatusHandler)
    }

    /**
     * Helper method to handle the boilerplate of starting a [JibriService].
     * Returns a [StartServiceResult] to denote whether the service was
     * started successfully or not.
     */
    private fun startService(
            jibriService: JibriService,
            serviceParams: ServiceParams,
            serviceStatusHandler: JibriServiceStatusHandler? = null): StartServiceResult {
        publishStatus(JibriStatusPacketExt.Status.BUSY)
        if (serviceStatusHandler != null) {
            jibriService.addStatusHandler(serviceStatusHandler)
        }
        // The manager adds its own status handler so that it can stop
        // the error'd service and update presence appropriately
        jibriService.addStatusHandler {
            when (it) {
                JibriServiceStatus.ERROR, JibriServiceStatus.FINISHED -> stopService()
            }
        }

        jibriService.start()
        currentActiveService = jibriService
        if (serviceParams.usageTimeoutMinutes != 0) {
            logger.info("This service will have a usage timeout of ${serviceParams.usageTimeoutMinutes} minute(s)")
            serviceTimeoutTask = executor.schedule(serviceParams.usageTimeoutMinutes.toLong(), TimeUnit.MINUTES) {
                logger.info("The usage timeout has elapsed, stopping the currently active service")
                stopService()
            }
        }

        return StartServiceResult.SUCCESS
    }

    /**
     * Stop the currently active [JibriService], if there is one
     */
    @Synchronized
    fun stopService() {
        logger.info("Stopping the current service")
        serviceTimeoutTask?.cancel(false)
        // Note that this will block until the service is completely stopped
        currentActiveService?.stop()
        currentActiveService = null
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
        return JibriHealth(
                busy = busy()
        )
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