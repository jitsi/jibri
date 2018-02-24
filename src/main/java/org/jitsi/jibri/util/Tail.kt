package org.jitsi.jibri.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * [Tail] will read from an infinite [InputStream] and make available the most
 * recent line via [mostRecentLine].  Because reading from the stream can block,
 * this class creates its own single-threaded [java.util.concurrent.ExecutorService]
 * to do the work.
 */
class Tail(stream: InputStream) {
    private val reader = BufferedReader(InputStreamReader(stream))
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>
    @Volatile var mostRecentLine: String = ""

    init {
        task = executor.submit {
            while (true) {
                mostRecentLine = reader.readLine()
            }
        }
    }

    fun stop() {
        task.cancel(true)
    }
}