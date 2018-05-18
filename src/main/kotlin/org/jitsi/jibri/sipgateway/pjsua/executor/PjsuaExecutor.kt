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
import org.jitsi.jibri.sipgateway.pjsua.util.PjsuaStatus
import org.jitsi.jibri.sipgateway.pjsua.util.getPjsuaStatus
import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.logStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(NameableThreadFactory("pjsua")),
    private val processBuilder: ProcessBuilder = ProcessBuilder()
) : MonitorableProcess {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val pjsuaOutputLogger = Logger.getLogger("pjsua")
    /**
     * The currently active (if any) pjsua process
     */
    private var currentPjsuaProc: ProcessWrapper? = null

    init {
        pjsuaOutputLogger.useParentHandlers = false
        pjsuaOutputLogger.addHandler(PjsuaFileHandler())
    }
    /**
     * Launch pjsua with the given [PjsuaExecutorParams]
     */
    fun launchPjsua(pjsuaExecutorParams: PjsuaExecutorParams): Boolean {
        val command = listOf(
            "pjsua",
            "--capture-dev=$CAPTURE_DEVICE",
            "--playback-dev=$PLAYBACK_DEVICE",
            "--id", "${pjsuaExecutorParams.sipClientParams.displayName} <sip:jibri@127.0.0.1>",
            "--config-file", CONFIG_FILE_LOCATION,
            "--log-file", "/tmp/pjsua.out",
            "sip:${pjsuaExecutorParams.sipClientParams.sipAddress}"
        )

        try {
            currentPjsuaProc = ProcessWrapper(
                command,
                mapOf("DISPLAY" to X_DISPLAY),
                processBuilder)
        } catch (t: Throwable) {
            logger.error("Error starting pjsua: $t")
            return false
        }
        return currentPjsuaProc?.let {
            it.start()
            logStream(it.getOutput(), pjsuaOutputLogger, executor)
            true
        } ?: run {
            false
        }
    }

    /**
     * Shutdown pjsua gracefully
     */
    fun stopPjsua() {
        logger.info("Stopping pjsua process")
        currentPjsuaProc?.apply {
            stop()
            waitFor(10, TimeUnit.SECONDS)
            if (isAlive) {
                logger.error("Pjsua didn't stop, killing pjsua")
                destroyForcibly()
            }
        }
        logger.info("Pjsua exited with value ${currentPjsuaProc?.exitValue()}")
    }

    override fun getExitCode(): Int? {
        return currentPjsuaProc?.let {
            if (it.isAlive) null else it.exitValue()
        }
    }

    override fun isHealthy(): Boolean {
        return currentPjsuaProc?.let {
            val (status, mostRecentOutput) = it.getPjsuaStatus()
            return@let when (status) {
                PjsuaStatus.HEALTHY -> {
                    logger.debug("Pjsua appears healthy: $mostRecentOutput")
                    true
                }
                PjsuaStatus.EXITED -> {
                    logger.debug("Pjsua exited with code ${getExitCode()}: $mostRecentOutput")
                    false
                }
            }
        } ?: run {
            false
        }
    }
}
