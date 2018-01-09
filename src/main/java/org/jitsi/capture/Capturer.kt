package org.jitsi.capture

/**
 * TODO: how to model the 'output'? that seems to be tied to the capturer, i don't
 * think it could be modeled separately?
 */
interface Capturer {
    /**
     * Start the capturer with the given parameters
     */
    fun start(capturerParams: CapturerParams)

    fun stop()
}