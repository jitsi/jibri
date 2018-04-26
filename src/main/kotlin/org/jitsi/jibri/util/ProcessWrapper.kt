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

import java.io.InputStream
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

/**
 * A wrapper around [Process] that implements
 * behaviors more useful to Jibri.  This isn't done
 * as a subclass because [Process] is abstract
 * and the actual implementation varies by platform, so
 * we'll leave that to the existing [Process] class.
 * Templated on the value that will be return from
 * [getStatus]
 */
abstract class ProcessWrapper<out StatusType>(
    command: List<String>,
    val environment: Map<String, String> = mapOf(),
    private val processBuilder: ProcessBuilder = ProcessBuilder()
) {
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
    fun exitValue() = process.exitValue()

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

    fun waitFor(timeout: Long, unit: TimeUnit): Boolean = process.waitFor(timeout, unit)

    fun stop() {
        tail.stop()
        tee.stop()
        val pid = pid(process)
        Runtime.getRuntime().exec("kill -s SIGINT $pid")
    }

    fun destroyForcibly() {
        process.destroyForcibly()
    }

    /**
     * Mimic the "pid" member of Java 9's [Process].  This can't be
     * an extension function as it gets called from a Java context
     * (which wouldn't see the extension function as a normal
     * member)
     */
    private fun pid(process: Process): Long {
        var pid: Long = -1
        try {
            if (process.javaClass.name == "java.lang.UNIXProcess") {
                val field: Field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                pid = field.getLong(process)
                field.isAccessible = false
            }
        } catch (e: Exception ) {
            pid = -1
        }
        return pid
    }

    /**
     * Returns an [InputStream] representing the output of the wrapped
     * process.  The [InputStream] is unique, meaning
     * that the [InputStream] returned from each
     * instance of this call can be read from independently,
     * without affecting the reads on other [InputStream]s
     */
    fun getOutput(): InputStream {
        return tee.addBranch()
    }

    abstract fun getStatus(): Pair<StatusType, String>

    protected fun getMostRecentLine(): String = tail.mostRecentLine
}
