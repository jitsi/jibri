package org.jitsi.jibri.selenium

import org.jitsi.jibri.CallParams

/**
 * Options that can be passed to JibriSelenium
 */
data class JibriSeleniumOptions(
        /**
         *  Custom location for the chrome binary
         */
        var customBinaryLocation: String? = null,
        /**
         *  Which X11 display to use
         */
        var display: String? = null,
        /**
         * The xmpp login information for the web client
         */
        val callParams: CallParams
)
