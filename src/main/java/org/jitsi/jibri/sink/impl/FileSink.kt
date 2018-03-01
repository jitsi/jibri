package org.jitsi.jibri.sink.impl

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.WriteableDirectory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * [FileSink] represents a sink which will write to a media file on the
 * filesystem
 */
class FileSink(recordingsDirectory: WriteableDirectory, callName: String, extension: String = ".mp4") : Sink {
    val file: File
    init {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val filename = "${callName}_${currentTime.format(formatter)}$extension"
        file = File(recordingsDirectory, filename)
    }

    /**
     * See [Sink.getPath]
     */
    override val path: String = file.path

    /**
     * See [Sink.getFormat]
     */
    override val format: String = file.extension

    /**
     * See [Sink.getOptions]
     */
    override val options: String = "-profile:v main -level 3.1"
}
