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
import org.jitsi.jibri.service.JibriServiceStateMachine
import org.jitsi.jibri.service.toJibriServiceEvent
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClient
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClientParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
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
    private val sipGatewayServiceParams: SipGatewayServiceParams
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
    private val stateMachine = JibriServiceStateMachine()
    //TODO: this will go away once we permeate the reactive stuff to the top
    private val allSubComponentsRunning = CompletableFuture<Boolean>()
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
        stateMachine.onStateTransition(this::onServiceStateChange)

        stateMachine.registerSubComponent(JibriSelenium.COMPONENT_ID)
        jibriSelenium.addStatusHandler { state ->
            stateMachine.transition(state.toJibriServiceEvent(JibriSelenium.COMPONENT_ID))
        }

        stateMachine.registerSubComponent(PjsuaClient.COMPONENT_ID)
        pjsuaClient.addStatusHandler { state ->
            stateMachine.transition(state.toJibriServiceEvent(PjsuaClient.COMPONENT_ID))
        }
    }

    private fun onServiceStateChange(@Suppress("UNUSED_PARAMETER") oldState: ComponentState, newState: ComponentState) {
        logger.info("Streaming service transition from state $oldState to $newState")
        publishStatus(newState)
    }

    /**
     * Starting a [SipGatewayServiceParams] involves the following steps:
     * 1) Start selenium and join the web call on display :0
     * 2) Start the SIP client to join the SIP call on display :1
     * There are already ffmpeg daemons running which are capturing from
     * each of the displays and writing to video devices which selenium
     * and pjsua will use
     */
    override fun start() {
        jibriSelenium.joinCall(
            sipGatewayServiceParams.callParams.callUrlInfo.copy(urlParams = SIP_GW_URL_OPTIONS))
        whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
            pjsuaClient.start()
        }
    }

    override fun stop() {
        processMonitorTask?.cancel(false)
        pjsuaClient.stop()
        jibriSelenium.leaveCallAndQuitBrowser()
    }
}
