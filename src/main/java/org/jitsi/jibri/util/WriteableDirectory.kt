package org.jitsi.jibri.util

import java.io.File
import java.io.IOException


/**
 * A [File] representing a directory described by [dirPath]
 * which it requires to be writable.  The directory doesn't need to
 * exist ([WriteableDirectory] will try and create it) but it must
 * be able to be created and, once created, must be able to be
 * written to.  If any of these conditions fails, the constructor
 * throws [IOException].
 */
class WriteableDirectory(dirPath: String) : File(dirPath) {
    init {
        val path = File(dirPath)
        if ((!path.isDirectory or !path.mkdirs()) and !path.canWrite()) {
            throw IOException("Unable to write to directory $dirPath")
        }
    }
}