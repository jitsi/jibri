package org.jitsi.capture

import org.jitsi.util.MonitorableProcess
import org.jitsi.sink.Sink

class UnsupportedOsException(override var message: String = "Jibri does not support this OS") : Exception()
{
}

interface Capturer : MonitorableProcess {
    /**
     * Start the capturer with the given parameters
     */
    fun start(capturerParams: CapturerParams, sink: Sink)

    /**
     * Stop the capturer
     */
    fun stop()
}