package org.jitsi.jibri.selenium

/**
 * Options that can be passed to JibriSelenium
 */
data class JibriSeleniumOptions(
        /**
         *  The base url of the server i.e. https://meet.jit.si
         */
        var baseUrl: String,
        /**
         *  Custom location for the chrome binary
         */
        var customBinaryLocation: String? = null,
        /**
         *  Which X11 display to use
         */
        var display: String? = null)
