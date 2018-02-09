package org.jitsi.jibri.service

/**
 * Interface implemented by all implemented [JibriService]s.  A [JibriService]
 * is responsible for an entire feature of Jibri, such as recording a call
 * to a file or streaming a call to a url.
 * 
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