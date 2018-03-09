package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.util.WriteableDirectory
import org.jitsi.jibri.util.extensions.error
import java.io.IOException
import java.util.logging.Logger

data class RecordingOptions(
    /**
     * The directory in which recordings should be created
     */
    val recordingDirectory: WriteableDirectory,
    /**
     * The params needed to join the call
     */
    val callParams: CallParams,
    /**
     * The filesystem path to the script which should be executed when
     *  the recording is finished.
     */
    val finalizeScriptPath: String
)

/**
 * [FileRecordingJibriService] is the [JibriService] responsible for joining
 * a web call, capturing its audio and video, and writing that audio and video
 * to a file to be replayed later.
 */
class FileRecordingJibriService(private val recordingOptions: RecordingOptions) :
        AbstractFfmpegSeleniumService(recordingOptions.callParams) {
    override val logger = Logger.getLogger(this::class.qualifiedName)

    override fun getSink(): Sink {
        // We always create a new sink here because each time a new one is needed we want it
        // to use a new file
        return FileSink(recordingOptions.recordingDirectory, recordingOptions.callParams.callUrlInfo.callName)
    }

    override fun stop() {
        super.stop()
        logger.info("Finalizing the recording")
        finalize()
    }

    /**
     * Helper to execute the finalize script and wait for its completion.
     * NOTE that this will block for however long the finalize script takes
     * to complete (by design)
     */
    private fun finalize() {
        try {
            val finalizeProc = Runtime.getRuntime().exec("${recordingOptions.finalizeScriptPath} ${recordingOptions.recordingDirectory}")
            finalizeProc.waitFor()
            logger.info("Recording finalize script finished with exit " +
                    "value: ${finalizeProc.exitValue()}")
        } catch (e: IOException) {
            logger.error("Failed to run finalize script: $e")
        }
    }
}
