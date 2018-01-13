package org.jitsi.jibri.sink

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Model of a jibri recording
 */
class Recording(val recordingsDirectory: File, callName: String, extension: String = ".mp4") : Sink
{
    val file: File?
    init {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val filename = callName + currentTime.format(formatter) + extension
        if (recordingsDirectory.mkdirs() or recordingsDirectory.isDirectory)
        {
            file = File(recordingsDirectory, filename)
            println("Using recording file " + file.toString())
        }
        else
        {
            println("Error creating directory: $recordingsDirectory")
            file = null
        }
    }

    override public fun getPath(): String? = file?.path

    override public fun getFormat(): String? = file?.extension

    override public fun getOptions(): String = "-profile:v main -level 3.1"

    override public fun finalize()
    {
        TODO()
    }

}