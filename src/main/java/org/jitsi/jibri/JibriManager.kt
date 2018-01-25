package org.jitsi.jibri

import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.service.*
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
 * The manager to create Jibri recording/streaming instances.
 */
class JibriManager(val config: JibriConfig) {
    private val logger = Logger.getLogger(this::class.simpleName)
    private var currentActiveService: JibriService? = null

    @Synchronized
    fun startFileRecording(fileRecordingParams: FileRecordingParams): StartServiceResult {
        if (busy()) {
            return StartServiceResult.BUSY
        }
        currentActiveService = JibriFileRecordingService(RecordingOptions(
                recordingDirectory = File(config.recordingDirectory),
                callUrlInfo = fileRecordingParams.callUrlInfo,
                finalizeScriptPath = config.finalizeRecordingScriptPath
        ))
        return startService(currentActiveService)
    }

    @Synchronized
    fun startStreaming(streamingParams: StreamingParams): StartServiceResult {
        if (busy()) {
            return StartServiceResult.BUSY
        }
        currentActiveService = JibriStreamingService(StreamingOptions(
                streamUrl = streamingParams.streamUrl,
                callUrlInfo = streamingParams.callUrlInfo
        ))
        return startService(currentActiveService)
    }

    @Synchronized
    fun startSipGateway(): StartServiceResult {
        TODO()
    }

    private fun startService(jibriService: JibriService?): StartServiceResult {
        jibriService?.let {
            it.start()
            return StartServiceResult.SUCCESS
        } ?: run {
            logger.error("Error starting requested service")
            return StartServiceResult.ERROR
        }
    }

    @Synchronized
    fun stopService() {
        currentActiveService?.stop()
        currentActiveService = null
    }

    @Synchronized
    fun busy(): Boolean {
        return currentActiveService != null
    }

    @Synchronized
    fun healthCheck(): JibriHealth {
        return JibriHealth(
                busy = busy()
        )
    }

}