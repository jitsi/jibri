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

package org.jitsi.jibri.service.impl

import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.selenium.SIP_GW_URL_OPTIONS
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClient
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClientParams
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.extensions.error
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

data class SipGatewayServiceParams(
    /**
     * The params needed to join the web call
     */
    val callParams: CallParams,
    /**
     * The params needed for bringing a SIP client into
     * the call
     */
    val sipClientParams: SipClientParams
)

/**
 * A [JibriService] responsible for joining both a web call
 * and a SIP call, capturing the audio and video from each, and
 * forwarding thenm to the other side.
 */
class SipGatewayJibriService(
    private val sipGatewayServiceParams: SipGatewayServiceParams,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : JibriService() {
    /**
     * The [Logger] for this class
     */
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * Used for the selenium interaction
     */
    private val jibriSelenium = JibriSelenium(
        JibriSeleniumOptions(
            displayName = sipGatewayServiceParams.sipClientParams.displayName,
            extraChromeCommandLineFlags = listOf("--alsa-input-device=plughw:1,1"))
    )
    /**
     * The SIP client we'll use to connect to the SIP call (currently only a
     * pjsua implementation exists)
     */
    private val pjsuaClient = PjsuaClient(PjsuaClientParams(sipGatewayServiceParams.sipClientParams))

    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null

    init {
        jibriSelenium.addStatusHandler {
            publishStatus(it)
        }
    }

    /**
     * Starting a [SipGatewayServiceParams] involves the following steps:
     * 1) Start selenium and join the web call on display :0
     * 2) Start the SIP client to join the SIP call on display :1
     * There are already ffmpeg daemons running which are capturing from
     * each of the displays and writing to video devices which selenium
     * and pjsua will use
     */
    override fun start(): Boolean {
        if (!jibriSelenium.joinCall(
                sipGatewayServiceParams.callParams.callUrlInfo.copy(urlParams = SIP_GW_URL_OPTIONS))
        ) {
            logger.error("Selenium failed to join the call")
            return false
        }
        if (!pjsuaClient.start()) {
            logger.error("Pjsua failed to start")
            return false
        }
        val processMonitor = createSipClientMonitor(pjsuaClient)
        processMonitorTask = executor.scheduleAtFixedRate(processMonitor, 30, 10, TimeUnit.SECONDS)
        return true
    }

    private fun createSipClientMonitor(process: SipClient): ProcessMonitor {
        return ProcessMonitor(process) { exitCode ->
            when (exitCode) {
                null -> {
                    logger.error("SipClient process is still running but no longer healthy")
                    publishStatus(JibriServiceStatus.ERROR)
                }
                0 -> {
                    logger.info("SipClient remote side hung up")
                    publishStatus(JibriServiceStatus.FINISHED)
                }
                2 -> {
                    logger.info("SipClient remote side busy")
                    publishStatus(JibriServiceStatus.ERROR)
                }
                else -> {
                    logger.info("Sip client exited with code $exitCode")
                    publishStatus(JibriServiceStatus.ERROR)
                }
            }
        }
    }

    override fun stop() {
        processMonitorTask?.cancel(false)
        pjsuaClient.stop()
        jibriSelenium.leaveCallAndQuitBrowser()
    }
}
