package org.jitsi.jibri.capture

import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.sink.Sink

class UnsupportedOsException(override var message: String = "Jibri does not support this OS") : Exception()

/**
 * [Capturer] represents a process which will capture media.  It implements
 * [MonitorableProcess] so that its state can be monitored so it can be
 * restarted if it dies.
 */
interface Capturer : MonitorableProcess {
    /**
     * Start the capturer with the given [Sink].  Returns
     * true if the [Capturer] was started successfully,
     * false otherwise.
     */
    fun start(sink: Sink): Boolean

    /**
     * Stop the capturer
     */
    fun stop()
}
