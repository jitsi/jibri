/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */

package org.jitsi.jibri.util

import kotlinx.coroutines.flow.SharedFlow
import org.jitsi.jibri.ProcessFailedToStart
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The same as [Process], but with the output modeled as a [SharedFlow] such that
 * it can be watched by multiple observers
 */
class ProcessWrapper(
    private val process: Process,
    val name: String? = null
) {
    val output: SharedFlow<String> = tail(process.inputStream)

    val pid: Long
        get() = process.pidValue

    val isAlive: Boolean
        get() = process.isAlive

    val exitValue: Int
        get() = process.exitValue()

    fun waitFor() = process.waitFor()

    fun waitFor(timeout: Duration) = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)

    fun destroyForcibly(): Process? = process.destroyForcibly()
}

fun runProcess(
    command: List<String>,
    name: String = command.first(),
    environment: Map<String, String> = mapOf()
): ProcessWrapper {
    try {
        return ProcessWrapper(
            ProcessBuilder().apply {
                this.command(command)
                redirectErrorStream(true)
                this.environment().putAll(environment)
            }.start(),
            name
        )
    } catch (t: Throwable) {
        throw ProcessFailedToStart("Process $name failed to start: ${t.message}")
    }
}

fun stopProcess(process: ProcessWrapper, runtime: Runtime = Runtime.getRuntime()) {
    runtime.exec("kill -s SIGINT ${process.pid}")
}
