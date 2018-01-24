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

/**
 * The manager to create Jibri recording/streaming instances.
 */
class JibriManager(val config: JibriConfig) {
    private val logger = Logger.getLogger(this::class.simpleName)
    private var currentActiveService: JibriService? = null

    @Synchronized
    fun startService(jibriServiceOptions: JibriServiceOptions): StartServiceResult {
        if (busy()) {
            return StartServiceResult.BUSY
        }
        //TODO: this logic will need to change a bit once the gateway stuff
        // comes in.  hoping there's a better structure for the params
        // here we can use (or, even better, separate calls for the different
        // types...file recording, streaming, gateway)
        currentActiveService = when (jibriServiceOptions.recordingSinkType) {
            RecordingSinkType.FILE -> JibriFileRecordingService(
                    RecordingOptions(
                            recordingDirectory = File(config.recordingDirectory),
                            callUrlInfo = jibriServiceOptions.callUrlInfo,
                            finalizeScriptPath = config.finalizeRecordingScriptPath
                    )
            )
            RecordingSinkType.STREAM -> JibriStreamingService(
                    StreamingOptions(
                            streamUrl = jibriServiceOptions.streamUrl!!,
                            callUrlInfo = jibriServiceOptions.callUrlInfo
                    )
            )
            else -> TODO()
        }
        currentActiveService?.let {
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