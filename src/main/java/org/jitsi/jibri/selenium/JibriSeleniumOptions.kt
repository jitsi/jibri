package org.jitsi.jibri.selenium

import org.jitsi.jibri.CallParams

/**
 * Options that can be passed to [JibriSelenium]
 */
data class JibriSeleniumOptions(
        var display: String? = null,
        /**
         * The parameters neccessary for joining a call
         */
        val callParams: CallParams
)
