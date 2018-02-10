package org.jitsi.jibri.util

open class StatusPublisher<T> {
    private val handlers: MutableList<(T) -> Unit> = mutableListOf()
    fun addStatusHandler(handler: (T) -> Unit) {
        handlers.add(handler)
    }

    protected fun publishStatus(status: T) {
        handlers.forEach { handler ->
            handler(status)
        }
    }
}