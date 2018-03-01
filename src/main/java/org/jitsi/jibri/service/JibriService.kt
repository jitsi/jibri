package org.jitsi.jibri.service

import org.jitsi.jibri.util.StatusPublisher

enum class JibriServiceStatus {
    FINISHED,
    ERROR
}

typealias JibriServiceStatusHandler = (JibriServiceStatus) -> Unit

/**
 * Interface implemented by all implemented [JibriService]s.  A [JibriService]
 * is responsible for an entire feature of Jibri, such as recording a call
 * to a file or streaming a call to a url.
 */
abstract class JibriService : StatusPublisher<JibriServiceStatus>() {
    /**
     * Starts this [JibriService]
     */
    abstract fun start(): Boolean

    /**
     * Stops this [JibriService]
     */
    abstract fun stop()
}