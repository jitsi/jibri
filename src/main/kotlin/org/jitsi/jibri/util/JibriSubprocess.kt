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

import org.jitsi.jibri.util.extensions.error
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.logging.Logger

class JibriSubprocess(
    private val name: String,
    private val processOutputLogger: Logger? = null,
    private val processFactory: ProcessFactory = ProcessFactory(),
    private val processStatePublisherProvider: (ProcessWrapper) -> ProcessStatePublisher =
        { process -> ProcessStatePublisher(name, process) }
) : StatusPublisher<ProcessState>() {
    private val logger = Logger.getLogger("${this::class.qualifiedName}.$name")
    private var processLoggerTask: Future<Boolean>? = null

    private var process: ProcessWrapper? = null
    private var processStatePublisher: ProcessStatePublisher? = null

    fun launch(command: List<String>, env: Map<String, String> = mapOf()) {
        logger.info("Starting $name with command ${command.joinToString(separator = " ")} ($command)")
        process = processFactory.createProcess(command, env)
        try {
            process?.let {
                it.start()
                processStatePublisher = processStatePublisherProvider(it)
                processStatePublisher!!.addStatusHandler(this::publishStatus)
                if (processOutputLogger != null) {
                    processLoggerTask = LoggingUtils.logOutputOfProcess(it, processOutputLogger)
                }
            } ?: run {
                throw Exception("Process was null")
            }
        } catch (t: Throwable) {
            logger.error("Error starting $name")
            process = null
            publishStatus(ProcessState(ProcessFailedToStart(), ""))
        }
    }

    private fun waitForProcessLoggerTaskToFinish(timeout: Duration) {
        try {
            processLoggerTask?.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            logger.error("Timed out waiting for process logger task to complete")
            processLoggerTask?.cancel(true)
        } catch (e: Exception) {
            logger.error("Exception while waiting for process logger task to complete", e)
            processLoggerTask?.cancel(true)
        }
    }

    fun stop() {
        logger.info("Stopping $name process")
        processStatePublisher?.stop()

        process?.apply {
            if (!stopAndWaitFor(Duration.ofSeconds(10))) {
                logger.error("Error trying to gracefully stop $name, destroying forcibly")
                if (!destroyForciblyAndWaitFor(Duration.ofSeconds(10))) {
                    logger.error("Error trying to destroy $name forcibly")
                }
            }
        }
        waitForProcessLoggerTaskToFinish(Duration.ofSeconds(10))

        try {
            logger.info("$name exited with value ${process?.exitValue}")
        } catch (e: IllegalThreadStateException) {
            logger.error("$name has still not exited!  Unable to stop it")
        }
    }
}
