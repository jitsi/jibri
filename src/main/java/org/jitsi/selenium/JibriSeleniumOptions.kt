package org.jitsi.selenium

/**
 * Options that can be passed to JibriSelenium
 */
data class JibriSeleniumOptions(
        var baseUrl: String, // The base url of the server i.e. https://meet.jit.si
        var customBinaryLocation: String? = null, // Custom location for the chrome binary
        var display: String? = null) // which X11 display to use
{
}