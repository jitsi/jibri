package org.jitsi.jibri

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.service.*
import org.jitsi.jibri.service.impl.FileRecordingJibriService
import org.jitsi.jibri.service.impl.StreamingJibriService
import org.jitsi.jibri.service.impl.RecordingOptions
import org.jitsi.jibri.service.impl.StreamingOptions
import java.io.File
import java.util.logging.Logger

enum class StartServiceResult {
    SUCCESS,
    BUSY,
    ERROR
}

data class FileRecordingParams(
        val callUrlInfo: CallUrlInfo
)

data class StreamingParams(
        val youTubeStreamKey: String,
        val callUrlInfo: CallUrlInfo
)

/**
 * [JibriManager] is responsible for managing the various services Jibri
 * provides, as well as providing an API to query the health of this Jibri
 * instance.  NOTE: currently Jibri only runs a single service at a time, so
 * if one is running, the Jibri will describe itself as busy
 */
class JibriManager(private val configFile: File) {
    private val logger = Logger.getLogger(this::class.simpleName)
    private lateinit var config: JibriConfig
    private var currentActiveService: JibriService? = null
    private var configReloadPending = false

    init {
        loadConfig(configFile)
    }

    private fun loadConfig(configFile: File) {
        config = jacksonObjectMapper().readValue(configFile)
    }

    /**
     * Starts a [FileRecordingJibriService] to record the call described
     * in the params to a file.  Returns a [StartServiceResult] to denote
     * whether the service was started successfully or not.
     */
    @Synchronized
    fun startFileRecording(fileRecordingParams: FileRecordingParams): StartServiceResult {
        logger.info("Starting a file recording with params: $fileRecordingParams")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            return StartServiceResult.BUSY
        }
        val service = FileRecordingJibriService(RecordingOptions(
                recordingDirectory = File(config.recordingDirectory),
                callUrlInfo = fileRecordingParams.callUrlInfo,
                finalizeScriptPath = config.finalizeRecordingScriptPath
        ))
        return startService(service)
    }

    /**
     * Starts a [StreamingJibriService] to capture the call described
     * in the params and stream it to a url.  Returns a [StartServiceResult] to
     * denote whether the service was started successfully or not.
     */
    @Synchronized
    fun startStreaming(streamingParams: StreamingParams): StartServiceResult {
        logger.info("Starting a stream with params: $streamingParams")
        if (busy()) {
            logger.info("Jibri is busy, can't start service")
            return StartServiceResult.BUSY
        }
        val service = StreamingJibriService(StreamingOptions(
                youTubeStreamKey = streamingParams.youTubeStreamKey,
                callUrlInfo = streamingParams.callUrlInfo
        ))
        return startService(service)
    }

    /**
     * Starts a [SipGatewayStreamingService] to capture the call described
     * in the params and stream it to a SIP call, as well as capturing the media
     * from the SIP call and streaming it to the other side.  Returns a
     * [StartServiceResult] to denote whether the service was started
     * successfully or not.
     */
    @Synchronized
    fun startSipGateway(): StartServiceResult {
        TODO()
    }

    /**
     * Helper method to handle the boilerplate of starting a [JibriService].
     * Returns a [StartServiceResult] to denote whether the service was
     * started successfully or not.
     */
    private fun startService(jibriService: JibriService): StartServiceResult {
        jibriService.start()
        currentActiveService = jibriService
        return StartServiceResult.SUCCESS
    }

    /**
     * Stop the currently active [JibriService], if there is one
     */
    @Synchronized
    fun stopService() {
        logger.info("Stopping the current service")
        //TODO: do we need to block the call on stopping everything?
        // this ends up blocking the request until everything is done
        // (finalize script, etc.) and it's not clear we want to block
        // sending the response on all of that (maybe yes, maybe no)
        currentActiveService?.stop()
        currentActiveService = null
        if (configReloadPending) {
            logger.info("Reloading configuration file")
            loadConfig(configFile)
            configReloadPending = false
        }
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
     * Reload the current Jibri configuration as soon as possible.  If a
     * service is currently active, the config will not be reloaded until
     * it finishes
     */
    @Synchronized
    fun reloadConfig() {
        logger.info("Scheduling a config reload")
        if (!busy()) {
            logger.info("Jibri not busy, reloading config now")
            loadConfig(configFile)
        } else {
            logger.info("Scheduling config reload for the next time we're not busy")
            configReloadPending = true
        }
    }

    /**
     * Returns whether or not this Jibri is currently "busy".   "Busy" is
     * is defined as "does not currently have the capacity to spin up another
     * service"
     */
    @Synchronized
    private fun busy(): Boolean {
        return currentActiveService != null
    }

}