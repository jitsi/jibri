package org.jitsi.jibri.util

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Class for publishing and subscribing to status.  Handles the management
 * of all subscribers (via [addStatusHandler]) and pushes updates to
 * those subscribers (synchronously) in [publishStatus].  Classes
 * interested in publishing their status should inherit from
 * [StatusPublisher].  The data passed as 'status' is templated
 * and can be anything the class wants.
 */
open class StatusPublisher<T> {
    private val handlers: MutableList<(T) -> Unit> = CopyOnWriteArrayList()
    /**
     * Add a status handler for this [StatusPublisher].  Handlers
     * will be notified synchronously in the order they were added.
     */
    fun addStatusHandler(handler: (T) -> Unit) {
        handlers.add(handler)
    }

    /**
     * The function a status publisher should call when it has
     * a new status to publish.  Note that handlers are notified synchronously
     * in the context of the thread which calls [publishStatus]
     */
    protected fun publishStatus(status: T) {
        handlers.forEach { handler ->
            handler(status)
        }
    }
}
