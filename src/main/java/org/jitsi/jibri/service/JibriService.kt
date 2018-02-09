package org.jitsi.jibri.service

import org.jitsi.jibri.util.StatusPublisher

/**
 * Interface implemented by all implemented [JibriService]s.  A [JibriService]
 * is responsible for an entire feature of Jibri, such as recording a call
 * to a file or streaming a call to a url.
 * 
 */
abstract class JibriService : StatusPublisher() {
    /**
     * Starts this [JibriService]
     */
    abstract fun start()

    /**
     * Stops this [JibriService]
     */
    abstract fun stop()
}