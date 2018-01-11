package org.jitsi.capture

import org.jitsi.MonitorableProcess

/**
 * TODO: how to model the 'output'? that seems to be tied to the capturer, i don't
 * think it could be modeled separately?
 */
interface Capturer : MonitorableProcess {
    /**
     * Start the capturer with the given parameters
     */
    fun start(capturerParams: CapturerParams)

    /**
     * Stop the capturer
     */
    fun stop()

    /**
     * TODO: does this make sense here?  a capturer isn't always doing
     * a recording
     */
    fun finalizeRecording()
}