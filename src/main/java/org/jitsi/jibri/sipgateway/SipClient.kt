package org.jitsi.jibri.sipgateway

import org.jitsi.jibri.util.MonitorableProcess

data class SipClientParams(
    /**
     * The SIP address we'll be connecting to
     */
    val sipAddress: String = "",
    /**
     * The display name we'll use for the web conference
     * in the pjsua call
     */
    val displayName: String = ""
)

interface SipClient : MonitorableProcess {
    /**
     * Start the [SipClient]
     */
    fun start()

    /**
     * Stop the [SipClient]
     */
    fun stop()
}
