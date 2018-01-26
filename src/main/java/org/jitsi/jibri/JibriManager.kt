package org.jitsi.jibri

import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.service.*
import org.jitsi.jibri.service.impl.FileRecordingJibriService
import org.jitsi.jibri.service.impl.StreamingJibriService
import org.jitsi.jibri.service.impl.RecordingOptions
import org.jitsi.jibri.service.impl.StreamingOptions
import org.jitsi.jibri.util.error
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
        val streamUrl: String,
        val callUrlInfo: CallUrlInfo
)

/**
 * [JibriManager] is responsible for managing the various services Jibri
 * provides, as well as providing an API to query the health of this Jibri
 * instance.  NOTE: currently Jibri only runs a single service at a time, so
 * if one is running, the Jibri will describe itself as busy
 */
class JibriManager(val config: JibriConfig) {
    private val logger = Logger.getLogger(this::class.simpleName)
    private var currentActiveService: JibriService? = null

    /**
     * Starts a [FileRecordingJibriService] to record the call described
     * in the params to a file.  Returns a [StartServiceResult] to denote
     * whether the service was started successfully or not.
     */
    @Synchronized
    fun startFileRecording(fileRecordingParams: FileRecordingParams): StartServiceResult {
        if (busy()) {
            return StartServiceResult.BUSY
        }
        currentActiveService = FileRecordingJibriService(RecordingOptions(
                recordingDirectory = File(config.recordingDirectory),
                callUrlInfo = fileRecordingParams.callUrlInfo,
                finalizeScriptPath = config.finalizeRecordingScriptPath
        ))
        return startService(currentActiveService)
    }

    /**
     * Starts a [StreamingJibriService] to capture the call described
     * in the params and stream it to a url.  Returns a [StartServiceResult] to
     * denote whether the service was started successfully or not.
     */
    @Synchronized
    fun startStreaming(streamingParams: StreamingParams): StartServiceResult {
        if (busy()) {
            return StartServiceResult.BUSY
        }
        currentActiveService = StreamingJibriService(StreamingOptions(
                streamUrl = streamingParams.streamUrl,
                callUrlInfo = streamingParams.callUrlInfo
        ))
        return startService(currentActiveService)
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
    private fun startService(jibriService: JibriService?): StartServiceResult {
        jibriService?.let {
            it.start()
            return StartServiceResult.SUCCESS
        } ?: run {
            logger.error("Error starting requested service")
            return StartServiceResult.ERROR
        }
    }

    /**
     * Stop the currently active [JibriService], if there is one
     */
    @Synchronized
    fun stopService() {
        currentActiveService?.stop()
        currentActiveService = null
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
    private fun busy(): Boolean {
        return currentActiveService != null
    }

}