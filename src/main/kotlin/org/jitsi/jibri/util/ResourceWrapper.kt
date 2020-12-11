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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import org.jitsi.jibri.capture.ffmpeg.FfmpegFileHandler
import org.jitsi.jibri.selenium.Selenium
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.secs

/**
 * A wrapper around a given [resource] which will automatically call the given [resourceCleanupFunc] when closed.
 */
abstract class ResourceWrapper<T>(
    val resource: T,
    private val resourceCleanupFunc: suspend (T) -> Unit
) : AutoCloseable {

    override fun close() {
        runBlocking(NonCancellable) {
            resourceCleanupFunc(resource)
        }
    }
}

/**
 * A [ResourceWrapper] which launches subprocess using the given command and logs its output
 * to the given logger.  Automatically stops the process and makes sure its logging has finished
 * when it is closed.
 */
class SubProcessResourceWrapper private constructor(
    process: ProcessWrapper,
    cleanupFunc: suspend (ProcessWrapper) -> Unit
) : ResourceWrapper<ProcessWrapper>(process, cleanupFunc) {
    companion object {
        operator fun invoke(process: ProcessWrapper, logger: Logger): ResourceWrapper<ProcessWrapper> {
            val loggerTask = logProcessOutput(process, logger)
            return SubProcessResourceWrapper(process) {
                stopProcess(it)
                if (!it.waitFor(10.secs)) {
                    it.destroyForcibly()
                    // TODO: how can we get visibility if this fails?  Separate logger?  Passing in
                    // a logger makes the API more awkward
                    it.waitFor(10.secs)
                }
                loggerTask.join()
            }
        }
    }
}

/**
 * Create a ResourceWrapper inline with a custom cleanup function
 */
fun <T> customResource(resource: T, cleanupFunc: suspend(T) -> Unit): ResourceWrapper<T> {
    return object : ResourceWrapper<T>(resource, cleanupFunc) {}
}

/**
 * Run the given block, passing it the wrapped resource [T].  When the block finishes, automatically call
 * [ResourceWrapper.close] to perform any cleanup.
 */
inline fun <T> withResource(wrapper: ResourceWrapper<T>, block: (T) -> Unit) {
    wrapper.use {
        block(it.resource)
    }
}

/**
 * Use [selenium] as a resource while the given [block] executes.  When [block] finishes, automatically call
 * [Selenium.leaveCallAndQuitBrowser].
 */
inline fun withSelenium(selenium: Selenium, block: (Selenium) -> Unit) {
    withResource(
        customResource(selenium) {
            it.leaveCallAndQuitBrowser()
        },
        block
    )
}

inline fun withSubprocess(
    command: List<String>,
    outputLogger: Logger,
    block: (ProcessWrapper) -> Unit
) {
    withResource(
        SubProcessResourceWrapper(
            runProcess(command),
            outputLogger
        ),
        block
    )
}

/**
 * Launch ffmpeg using the given command, and log its output using the [FfmpegFileHandler].  When the given block
 * finishes, the ffmpeg process will automatically be stopped and will suspend until the logger finishes writing.
 */
inline fun withFfmpeg(command: List<String>, block: (ProcessWrapper) -> Unit) {
    withSubprocess(command, getLoggerWithHandler("ffmpeg", FfmpegFileHandler), block)
}
