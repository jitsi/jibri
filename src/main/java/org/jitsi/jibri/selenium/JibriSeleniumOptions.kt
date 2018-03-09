package org.jitsi.jibri.selenium

import org.jitsi.jibri.CallParams

/**
 * Options that can be passed to [JibriSelenium]
 */
data class JibriSeleniumOptions(
    /**
     * The parameters necessary for joining a call
     */
    val callParams: CallParams,
    /**
     * Which display selenium should be started on
     */
    var display: String = ":0"
)
