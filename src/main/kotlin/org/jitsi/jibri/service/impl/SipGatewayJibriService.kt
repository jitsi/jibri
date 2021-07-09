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

import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.selenium.SIP_GW_URL_OPTIONS
import org.jitsi.jibri.service.ErrorSettingPresenceFields
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceFinalizer
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.getSipAddress
import org.jitsi.jibri.sipgateway.getSipScheme
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClient
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClientParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.ProcessFactory
import org.jitsi.jibri.util.whenever
import java.time.Duration
import java.util.concurrent.ScheduledFuture

data class SipGatewayServiceParams(
    /**
     * The params needed to join the web call
     */
    val callParams: CallParams,
    /**
     * The login information needed to use when establishing the call
     */
    val callLoginParams: XmppCredentials?,
    /**
     * The params needed for bringing a SIP client into
     * the call
     */
    val sipClientParams: SipClientParams
)

private const val FINALIZE_SCRIPT_LOCATION = "/opt/jitsi/jibri/finalize_sip.sh"

/**
 * A [JibriService] responsible for joining both a web call
 * and a SIP call, capturing the audio and video from each, and
 * forwarding them to the other side.
 */
class SipGatewayJibriService(
    private val sipGatewayServiceParams: SipGatewayServiceParams,
    jibriSelenium: JibriSelenium? = null,
    pjsuaClient: PjsuaClient? = null,
    processFactory: ProcessFactory = ProcessFactory(),
    private val jibriServiceFinalizer: JibriServiceFinalizer = JibriServiceFinalizeCommandRunner(
        processFactory,
        listOf(FINALIZE_SCRIPT_LOCATION)
    )
) : StatefulJibriService("SIP gateway") {
    /**
     * Used for the selenium interaction
     */
    private val jibriSelenium = jibriSelenium ?: JibriSelenium(
        logger,
        JibriSeleniumOptions(
            displayName = if (sipGatewayServiceParams.callParams.displayName.isNotBlank()) {
                sipGatewayServiceParams.callParams.displayName
            } else if (sipGatewayServiceParams.sipClientParams.sipAddress.isNotBlank()) {
                sipGatewayServiceParams.sipClientParams.sipAddress.substringBeforeLast("@")
            } else {
                sipGatewayServiceParams.sipClientParams.displayName
            },
            email = sipGatewayServiceParams.callParams.email,
            callStatsUsernameOverride = sipGatewayServiceParams.callParams.callStatsUsernameOverride,
            // by default we wait 30 minutes alone in the call before deciding to hangup
            emptyCallTimeout = Duration.ofMinutes(30),
            extraChromeCommandLineFlags = listOf("--alsa-input-device=plughw:1,1"),
            enableLocalParticipantStatusChecks = true
        )
    )

    /**
     * The SIP client we'll use to connect to the SIP call (currently only a
     * pjsua implementation exists)
     */
    private val pjsuaClient = pjsuaClient ?: PjsuaClient(
        logger,
        PjsuaClientParams(sipGatewayServiceParams.sipClientParams)
    )

    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null

    init {
        registerSubComponent(JibriSelenium.COMPONENT_ID, this.jibriSelenium)
        registerSubComponent(PjsuaClient.COMPONENT_ID, this.pjsuaClient)
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
            sipGatewayServiceParams.callParams.callUrlInfo.copy(urlParams = SIP_GW_URL_OPTIONS),
            sipGatewayServiceParams.callLoginParams,
            sipGatewayServiceParams.callParams.passcode
        )

        // when in auto-answer mode we want to start as quick as possible as
        // we will be waiting for a sip call to come
        if (sipGatewayServiceParams.sipClientParams.autoAnswer) {
            pjsuaClient.start()
            whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
                logger.info("Selenium joined the call")
                addSipMetadataToPresence()
            }
        } else {
            whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
                logger.info("Selenium joined the call, starting pjsua")
                pjsuaClient.start()
                addSipMetadataToPresence()
            }
        }
    }

    private fun addSipMetadataToPresence() {
        try {
            val sipAddress = sipGatewayServiceParams.sipClientParams.sipAddress.getSipAddress()
            val sipScheme = sipGatewayServiceParams.sipClientParams.sipAddress.getSipScheme()
            jibriSelenium.addToPresence("sip_address", "$sipScheme:$sipAddress")
            jibriSelenium.sendPresence()
        } catch (t: Throwable) {
            logger.error("Error while setting fields in presence", t)
            publishStatus(ComponentState.Error(ErrorSettingPresenceFields))
        }
    }

    override fun stop() {
        processMonitorTask?.cancel(false)
        pjsuaClient.stop()
        jibriSelenium.leaveCallAndQuitBrowser()
        jibriServiceFinalizer.doFinalize()
    }
}
