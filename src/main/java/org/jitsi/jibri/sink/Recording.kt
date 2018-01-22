package org.jitsi.jibri.sink

import org.jitsi.jibri.util.error
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Model of a jibri recording
 */
class Recording(val recordingsDirectory: File, callName: String, extension: String = ".mp4") : Sink {
    private val logger = Logger.getLogger(this::class.simpleName)
    val file: File?
    init {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val filename = callName + currentTime.format(formatter) + extension
        if (recordingsDirectory.mkdirs() or recordingsDirectory.isDirectory)
        {
            file = File(recordingsDirectory, filename)
            logger.info("Using recording file " + file.toString())
        }
        else
        {
            logger.error("Error creating directory: $recordingsDirectory")
            file = null
        }
    }

    override fun getPath(): String? = file?.path

    override fun getFormat(): String? = file?.extension

    override fun getOptions(): String = "-profile:v main -level 3.1"

    override fun finalize(finalizeScriptPath: String)
    {
        logger.info("Finalizing the recording")
        try {
            val finalizeProc = Runtime.getRuntime().exec(finalizeScriptPath)
            finalizeProc.waitFor()
            logger.info("Recording finalize script finished with exit " +
                    "value: ${finalizeProc.exitValue()})")
        } catch (e: IOException) {
            logger.error("Failed to run finalize script: $e")
        }
    }
}