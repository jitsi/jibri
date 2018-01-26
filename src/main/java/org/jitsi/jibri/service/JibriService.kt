package org.jitsi.jibri.service

/**
 * Interface implemented by all implemented [JibriService]s.  A [JibriService]
 * is responsible for an entire feature of Jibri, such as recording a call
 * to a file, streaming a call to a url, or acting as a gateway between a
 * web call and a SIP call.
 */
interface JibriService {
    /**
     * Starts this [JibriService]
     */
    fun start()

    /**
     * Stops this [JibriService]
     */
    fun stop()
}