package org.jitsi.jibri.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Reads from the given [InputStream] and mirrors the read
 * data to all of the created 'branches' off of it.
 * All branches will 'receive' all data from the original
 * [InputStream] starting at the the point of
 * the branch's creation.
 * NOTE: This class will not read from the given [InputStream]
 * automatically, its [read] must be invoked
 * to read the data from the original stream and write it to
 * the branches
 */
class TeeLogic(inputStream: InputStream) {
    val reader = BufferedReader(InputStreamReader(inputStream))
    var branches = CopyOnWriteArrayList<OutputStream>()

    /**
     * Reads a byte from the original [InputStream] and
     * writes it to all of the branches
     */
    fun read() {
        val c = reader.read()
        branches.forEach {
            it.write(c)
        }
    }

    /**
     * Returns an [InputStream] that will receive
     * all data from the original [InputStream]
     * starting from the time of its creation
     */
    fun addBranch(): InputStream {
        val outputStream = PipedOutputStream()
        branches.add(outputStream)
        return PipedInputStream(outputStream)
    }
}

/**
 * A wrapper around [TeeLogic] which spins up its own thread
 * to do the reading automatically
 */
class Tee(inputStream: InputStream) {
    private val teeLogic = TeeLogic(inputStream)
    private val executor = Executors.newSingleThreadExecutor(NameableThreadFactory("Tee"))
    private val task: Future<*>

    init {
        task = executor.submit {
            while (true) {
                teeLogic.read()
            }
        }
    }

    fun addBranch(): InputStream {
        return teeLogic.addBranch()
    }

    fun stop() {
        task.cancel(true)
    }
}
