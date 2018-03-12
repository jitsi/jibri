package org.jitsi.jibri.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Read from an infinite [InputStream] and make available the most
 * recent line that was read via [mostRecentLine]. NOTE: this class
 * will not read from the stream automatically, its [readLine]
 * method must be called.
 */
class TailLogic(inputStream: InputStream) {
    private val reader = BufferedReader(InputStreamReader(inputStream))
    @Volatile var mostRecentLine: String = ""

    fun readLine() {
        mostRecentLine = reader.readLine()
    }
}

/**
 * A wrapper around [TailLogic] which Spins up a thread to constantly
 * read from the given [InputStream] and save the most-recently-read
 * line as [mostRecentLine] to be read by whomever is interested.
 */
class Tail(inputStream: InputStream) {
    private val tailLogic = TailLogic(inputStream)
    private val executor = Executors.newSingleThreadExecutor(NameableThreadFactory("Tail"))
    private var task: Future<*>
    var mostRecentLine: String = ""
        get() {
            return tailLogic.mostRecentLine
        }

    init {
        task = executor.submit {
            while (true) {
                tailLogic.readLine()
            }
        }
    }

    fun stop() {
        task.cancel(true)
    }
}
