package org.jitsi.jibri.util

import java.util.concurrent.ThreadFactory

/**
 * A helper to create a [ThreadFactory] where all threads will
 * be given [name]
 */
class NameableThreadFactory(private val name: String) : ThreadFactory {
    override fun newThread(r: Runnable?): Thread {
        return Thread(r, name)
    }
}