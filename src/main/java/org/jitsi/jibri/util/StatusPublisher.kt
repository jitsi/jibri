package org.jitsi.jibri.util

enum class Status {
    FINISHED,
    ERROR
}

typealias StatusHandler = (Status) -> Unit

open class StatusPublisher {
    private val handlers: MutableList<StatusHandler> = mutableListOf()
    fun addStatusHandler(handler: StatusHandler) {
        handlers.add(handler)
    }

    protected fun publishStatus(status: Status) {
        handlers.forEach { handler ->
            handler(status)
        }
    }
}