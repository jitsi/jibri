/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jitsi.jibri.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future

class EndOfStreamException : Exception()

private const val EOF = -1

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
    private val reader = BufferedReader(InputStreamReader(inputStream))
    private var branches = CopyOnWriteArrayList<OutputStream>()

    /**
     * Reads a byte from the original [InputStream] and
     * writes it to all of the branches.  If EOF is detected,
     * all branches will be closed and [EndOfStreamException]
     * will be thrown, so that any callers can know not
     * to bother calling again.
     */
    fun read() {
        val c = reader.read()
        if (c == EOF) {
            branches.forEach(OutputStream::close)
            throw EndOfStreamException()
        } else {
            branches.forEach { it.write(c) }
        }
    }

    /**
     * If you want to close the Tee before the underlying
     * [InputStream] closes, you'll need to call [close] to
     * properly close all downstream branches. Note that
     * calling [read] after [close] when there are branches
     * will result in [java.io.IOException].
     */
    fun close() {
        branches.forEach(OutputStream::close)
    }

    /**
     * Returns an [InputStream] that will receive
     * all data from the original [InputStream]
     * starting from the time of its creation
     */
    fun addBranch(): InputStream {
        with(PipedOutputStream()) {
            branches.add(this)
            return PipedInputStream(this)
        }
    }
}

/**
 * A wrapper around [TeeLogic] which spins up its own thread
 * to do the reading automatically
 */
class Tee(inputStream: InputStream) {
    private val teeLogic = TeeLogic(inputStream)
    private val task: Future<*>

    init {
        task = TaskPools.ioPool.submit {
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
        teeLogic.close()
    }
}
