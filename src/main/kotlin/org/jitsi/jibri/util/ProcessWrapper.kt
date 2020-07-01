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

import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.pidValue
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * A wrapper around [Process] that implements
 * behaviors more useful to Jibri.  This isn't done
 * as a subclass because [Process] is abstract
 * and the actual implementation varies by platform.
 *
 * NOTE that many methods in this class throw exceptions
 * which must be handled
 */
class ProcessWrapper(
    command: List<String>,
    val environment: Map<String, String> = mapOf(),
    private val processBuilder: ProcessBuilder = ProcessBuilder(),
    private val runtime: Runtime = Runtime.getRuntime()
) {
    private val logger = Logger.getLogger("${this::class.qualifiedName}")
    /**
     * The actual underlying [Process] this wrapper
     * wraps
     */
    private lateinit var process: Process

    /**
     * A 'Tee' which allows us to split the stdout
     * stream coming from the process into multiple
     * independent streams which can be read from
     * independently
     */
    private lateinit var tee: Tee

    /**
     * Observes the most recent line of output from
     * the wrapped process
     */
    private lateinit var tail: Tail

    /**
     * Whether or not the wrapped process is alive
     */
    val isAlive: Boolean
        get() = process.isAlive

    /**
     * Returns the exit value of the wrapped process.
     * Throws if the wrapped process has not exited, so check
     * that [isAlive] is false before calling to avoid that.
     */
    val exitValue: Int
        get() = process.exitValue()

    init {
        processBuilder.command(command)
        processBuilder.redirectErrorStream(true)
        processBuilder.environment().putAll(environment)
    }

    /**
     * Starts this Process.  Will throw if there's an error
     * (see [ProcessBuilder.start])
     */
    fun start() {
        process = processBuilder.start()
        tee = Tee(process.inputStream)
        tail = Tail(getOutput())
    }

    fun waitFor() = process.waitFor()

    fun waitFor(timeout: Long, unit: TimeUnit): Boolean = process.waitFor(timeout, unit)

    fun stop() {
        // Note that we specifically do NOT call stop on tee or tail here
        // because we want them to read everything available from the
        // process' inputstream. Once it's done, they'll read
        // the EOF and close things up correctly
        runtime.exec("kill -s SIGINT ${process.pidValue}")
    }

    /**
     * A convenience method on top of [stop] and [waitFor] which
     * not only combines the two calls but handles any exceptions
     * thrown.  'true' is returned if the process was successfully
     * ended, false otherwise
     */
    fun stopAndWaitFor(timeout: Duration): Boolean {
        return try {
            stop()
            waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            logger.error("Error stopping process", t)
            false
        }
    }

    fun destroyForcibly(): Process = process.destroyForcibly()

    /**
     * A convenience method on top of [destroyForcibly] and [waitFor] which
     * not only combines the two calls but handles any exceptions
     * thrown.  'true' is returned if the process was successfully
     * ended, false otherwise
     */
    fun destroyForciblyAndWaitFor(timeout: Duration): Boolean {
        return try {
            destroyForcibly()
            waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            logger.error("Error forcibly destroying process", t)
            false
        }
    }

    /**
     * Returns an [InputStream] representing the output of the wrapped
     * process.  The [InputStream] is unique, meaning
     * that the [InputStream] returned from each
     * instance of this call can be read from independently,
     * without affecting the reads on other [InputStream]s
     */
    fun getOutput(): InputStream = tee.addBranch()

    fun getMostRecentLine(): String = tail.mostRecentLine
}
