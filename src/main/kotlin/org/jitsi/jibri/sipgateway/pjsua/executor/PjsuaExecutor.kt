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

package org.jitsi.jibri.sipgateway.pjsua.executor

import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.util.PjsuaFileHandler
import org.jitsi.jibri.util.LoggingUtils
import org.jitsi.jibri.util.ProcessFactory
import org.jitsi.jibri.util.ProcessFailedToStart
import org.jitsi.jibri.util.ProcessState
import org.jitsi.jibri.util.ProcessStatePublisher
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.getLoggerWithHandler
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

data class PjsuaExecutorParams(
    val sipClientParams: SipClientParams
)

private const val CAPTURE_DEVICE = 23
private const val PLAYBACK_DEVICE = 24
private const val CONFIG_FILE_LOCATION = "/home/jibri/pjsua.config"
private const val X_DISPLAY = ":1"

class PjsuaExecutor(
    private val processFactory: ProcessFactory = ProcessFactory(),
    private val processStatePublisherProvider: (ProcessWrapper) -> ProcessStatePublisher = ::ProcessStatePublisher
) : StatusPublisher<ProcessState>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private var processLoggerTask: Future<Boolean>? = null
    /**
     * The currently active (if any) pjsua process
     */
    private var currentPjsuaProc: ProcessWrapper? = null
    private var processStatePublisher: ProcessStatePublisher? = null

    companion object {
        private val pjsuaOutputLogger = getLoggerWithHandler("pjsua", PjsuaFileHandler())
    }

    /**
     * Launch pjsua with the given [PjsuaExecutorParams]
     */
    fun launchPjsua(pjsuaExecutorParams: PjsuaExecutorParams) {
        val command = listOf(
            "pjsua",
            "--capture-dev=$CAPTURE_DEVICE",
            "--playback-dev=$PLAYBACK_DEVICE",
            "--id", "${pjsuaExecutorParams.sipClientParams.displayName} <sip:jibri@127.0.0.1>",
            "--config-file", CONFIG_FILE_LOCATION,
            "--log-file", "/tmp/pjsua.out",
            "sip:${pjsuaExecutorParams.sipClientParams.sipAddress}"
        )

        currentPjsuaProc = processFactory.createProcess(
                command,
                mapOf("DISPLAY" to X_DISPLAY)
        ).also {
            try {
                it.start()
                processStatePublisher = processStatePublisherProvider(it)
                processStatePublisher!!.addStatusHandler(this::publishStatus)
                processLoggerTask = LoggingUtils.logOutput(it, pjsuaOutputLogger)
            } catch (t: Throwable) {
                logger.error("Error starting pjsua: $t")
                currentPjsuaProc = null
                publishStatus(ProcessState(ProcessFailedToStart(), ""))
            }
        }
    }

    /**
     * Shutdown pjsua gracefully
     */
    fun stopPjsua() {
        logger.info("Stopping pjsua process")
        processStatePublisher?.stop()
        currentPjsuaProc?.apply {
            stop()
            waitFor(10, TimeUnit.SECONDS)
            if (isAlive) {
                logger.error("Pjsua didn't stop, killing pjsua")
                destroyForcibly()
            }
        }
        processLoggerTask?.get()
        logger.info("Pjsua exited with value ${currentPjsuaProc?.exitValue}")
    }
}
