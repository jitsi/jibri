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

package org.jitsi.jibri.sipgateway.pjsua

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.getSipAddress
import org.jitsi.jibri.sipgateway.getSipScheme
import org.jitsi.jibri.sipgateway.pjsua.util.PjsuaExitedPrematurely
import org.jitsi.jibri.sipgateway.pjsua.util.RemoteSipClientBusy
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.ProcessExited
import org.jitsi.metaconfig.from
import org.jitsi.metaconfig.optionalconfig
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths

data class PjsuaClientParams(
    val sipClientParams: SipClientParams
)

private const val PJSUA_SCRIPT_FILE_LOCATION = "/opt/jitsi/jibri/pjsua.sh"
private const val X_DISPLAY = ":1"
private const val DTMF_FIFO_PATH = "/tmp/jibri_pjsua_dtmf"

class PjsuaClient(
    parentLogger: Logger,
    private val pjsuaClientParams: PjsuaClientParams,
    private val onDtmfCommand: ((String) -> Unit)? = null
) : SipClient() {
    private val logger = createChildLogger(parentLogger)
    private val pjsua: JibriSubprocess = JibriSubprocess(logger, "pjsua")
    private val sipOutboundPrefix: String? by optionalconfig(
        "jibri.sip.outbound-prefix".from(Config.configSource)
    )
    private var dtmfReaderThread: Thread? = null

    companion object {
        const val COMPONENT_ID = "Pjsua"
    }

    init {
        pjsua.addStatusHandler { processState ->
            when {
                processState.runningState is ProcessExited -> {
                    when (processState.runningState.exitCode) {
                        // TODO: add detail?
                        // Remote side hung up
                        0 -> publishStatus(ComponentState.Finished)
                        2 -> publishStatus(ComponentState.Error(RemoteSipClientBusy))
                        else -> publishStatus(
                            ComponentState.Error(PjsuaExitedPrematurely(processState.runningState.exitCode))
                        )
                    }
                }
                // TODO: i think everything else just counts as running?
                else -> publishStatus(ComponentState.Running)
            }
        }
    }

    override fun start() {
        if (onDtmfCommand != null) {
            createDtmfFifo()
            startDtmfFifoReader()
        }

        val command = mutableListOf(
            PJSUA_SCRIPT_FILE_LOCATION
        )

        val sipAddress = pjsuaClientParams.sipClientParams.sipAddress.getSipAddress()
        val sipScheme = pjsuaClientParams.sipClientParams.sipAddress.getSipScheme()

        if (pjsuaClientParams.sipClientParams.userName != null &&
            pjsuaClientParams.sipClientParams.password != null
        ) {
            command.add(
                "--id=${pjsuaClientParams.sipClientParams.displayName} " +
                    "<$sipScheme:${pjsuaClientParams.sipClientParams.userName}>"
            )
            command.add(
                "--registrar=$sipScheme:${
                    pjsuaClientParams.sipClientParams.userName.substringAfter('@')
                }"
            )
            command.add("--realm=*")
            command.add("--username=${pjsuaClientParams.sipClientParams.userName.substringBefore('@')}")
            command.add("--password=${pjsuaClientParams.sipClientParams.password}")
        }

        if (!pjsuaClientParams.sipClientParams.contact.isNullOrEmpty()) {
            command.add("--contact=${pjsuaClientParams.sipClientParams.contact}")
        }

        if (!pjsuaClientParams.sipClientParams.proxy.isNullOrEmpty()) {
            command.add("--proxy=${pjsuaClientParams.sipClientParams.proxy}")
        }

        if (pjsuaClientParams.sipClientParams.autoAnswer) {
            command.add("--auto-answer-timer=${pjsuaClientParams.sipClientParams.autoAnswerTimer}")
            command.add("--auto-answer=200")
        } else {
            // The proxy we'll use for all the outgoing SIP requests;
            // This proxy will be enabled if --proxy is not set explicitly through API
            // The client should not specify a Route header in the sip INVITE message. Using hide will let the server set the Route header
            if (pjsuaClientParams.sipClientParams.proxy == null &&
                pjsuaClientParams.sipClientParams.userName != null
            ) {
                command.add(
                    "--proxy=$sipScheme:${pjsuaClientParams.sipClientParams.userName.substringAfter('@')};" +
                        "transport=tcp;hide"
                )
            }

            if (sipOutboundPrefix.isNullOrEmpty()) {
                command.add("$sipScheme:$sipAddress")
            } else {
                command.add("$sipScheme:${sipOutboundPrefix}$sipAddress")
            }
        }

        pjsua.launch(command, mapOf("DISPLAY" to X_DISPLAY))
    }

    private fun createDtmfFifo() {
        try {
            Files.deleteIfExists(Paths.get(DTMF_FIFO_PATH))
            val process = Runtime.getRuntime().exec(arrayOf("mkfifo", DTMF_FIFO_PATH))
            process.waitFor()
            logger.info("Created DTMF FIFO at $DTMF_FIFO_PATH")
        } catch (e: Exception) {
            logger.error("Failed to create DTMF FIFO", e)
        }
    }

    private fun startDtmfFifoReader() {
        dtmfReaderThread = Thread {
            try {
                logger.info("DTMF FIFO reader waiting for pjsua to connect")
                FileInputStream(File(DTMF_FIFO_PATH)).use { fis ->
                    BufferedReader(InputStreamReader(fis)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            if (line.startsWith("DTMF_COMMAND:")) {
                                onDtmfCommand?.invoke(line.removePrefix("DTMF_COMMAND:"))
                            }
                            line = reader.readLine()
                        }
                    }
                }
                logger.info("DTMF FIFO reader exited (EOF)")
            } catch (e: Exception) {
                logger.info("DTMF FIFO reader exited: ${e.message}")
            }
        }.also {
            it.isDaemon = true
            it.name = "dtmf-fifo-reader"
            it.start()
        }
    }

    override fun stop() {
        pjsua.stop()
        stopDtmfFifoReader()
    }

    private fun stopDtmfFifoReader() {
        val fifoFile = File(DTMF_FIFO_PATH)
        if (!fifoFile.exists()) return

        // Open the write end to unblock dtmfReaderThread if it's blocking on open()
        val unblockThread = Thread {
            try {
                FileOutputStream(fifoFile).close()
            } catch (e: Exception) {}
        }.also {
            it.isDaemon = true
            it.start()
        }

        dtmfReaderThread?.join(2000)
        unblockThread.join(2000)

        try {
            Files.deleteIfExists(Paths.get(DTMF_FIFO_PATH))
        } catch (e: Exception) {}
    }
}
