package org.jitsi

import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Model of a jibri recording
 */
class Recording(val recordingsPath: File, callName: String, extension: String = ".mp4") : Sink
{
    val file: File?
    init {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val filename = callName + currentTime.format(formatter) + extension
        if (recordingsPath.mkdirs() or recordingsPath.isDirectory)
        {
            file = File(recordingsPath, filename)
            println("Using recording file " + file.toString())
        }
        else
        {
            println("Error creating directory: $recordingsPath")
            file = null
        }
    }

    override public fun getPath(): String? = file?.path

    override public fun finalize()
    {
        TODO()
    }

}