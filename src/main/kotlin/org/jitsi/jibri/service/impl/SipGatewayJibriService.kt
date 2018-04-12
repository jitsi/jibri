package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.selenium.SIP_GW_URL_OPTIONS
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClient
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClientParams
import org.jitsi.jibri.util.extensions.error
import java.util.logging.Logger

data class SipGatewayServiceParams(
    /**
     * The params needed to join the web call
     */
    val callParams: CallParams,
    val sipClientParams: SipClientParams
)

class SipGatewayJibriService(private val sipGatewayServiceParams: SipGatewayServiceParams) : JibriService() {
    /**
     * The [Logger] for this class
     */
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * Used for the selenium interaction
     */
    private val jibriSelenium = JibriSelenium(
        JibriSeleniumOptions(
            sipGatewayServiceParams.callParams,
            displayName = sipGatewayServiceParams.sipClientParams.displayName,
            urlParams = SIP_GW_URL_OPTIONS,
            extraChromeCommandLineFlags = listOf("--alsa-input-device=plughw:1,1")))
    /**
     * The SIP client we'll use to connect to the SIP call (currently only a
     * pjsua implementation exists)
     */
    private val pjsuaClient = PjsuaClient(PjsuaClientParams(sipGatewayServiceParams.sipClientParams))

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
        if (!jibriSelenium.joinCall(sipGatewayServiceParams.callParams.callUrlInfo.callName)) {
            logger.error("Selenium failed to join the call")
            return false
        }
        if (!pjsuaClient.start()) {
            logger.error("Pjsua failed to start")
            return false
        }
        //TODO: process monitor
        return true
    }

    override fun stop() {
        pjsuaClient.stop()
        jibriSelenium.leaveCallAndQuitBrowser()
    }
}
