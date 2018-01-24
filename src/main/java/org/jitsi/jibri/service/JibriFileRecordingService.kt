package org.jitsi.jibri.service

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.sink.FileSink
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.error
import java.io.File
import java.io.IOException
import java.util.logging.Logger

data class RecordingOptions(
        // The directory in which recordings should be created
        val recordingDirectory: File,
        val callUrlInfo: CallUrlInfo,
        val finalizeScriptPath: String
)

class JibriFileRecordingService(val recordingOptions: RecordingOptions) :
        JibriSeleniumFfmpegService(recordingOptions.callUrlInfo) {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val sink: Sink
    init {
        sink = FileSink(
                recordingsDirectory = recordingOptions.recordingDirectory,
                callName = recordingOptions.callUrlInfo.callName
        )
    }

    override fun getSink(): Sink = sink

    override fun finalize() {
        try {
            val finalizeProc = Runtime.getRuntime().exec(recordingOptions.finalizeScriptPath)
            finalizeProc.waitFor()
            logger.info("Recording finalize script finished with exit " +
                    "value: ${finalizeProc.exitValue()}")
        } catch (e: IOException) {
            logger.error("Failed to run finalize script: $e")
        }
    }
}