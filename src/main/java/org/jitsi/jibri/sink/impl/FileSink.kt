package org.jitsi.jibri.sink.impl

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.error
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * [FileSink] represents a sink which will write to a media file on the
 * filesystem
 */
class FileSink(val recordingsDirectory: File, callName: String, extension: String = ".mp4") : Sink {
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

    /**
     * See [Sink.getPath]
     */
    override fun getPath(): String? = file?.path

    /**
     * See [Sink.getFormat]
     */
    override fun getFormat(): String? = file?.extension

    /**
     * See [Sink.getOptions]
     */
    override fun getOptions(): String = "-profile:v main -level 3.1"
}