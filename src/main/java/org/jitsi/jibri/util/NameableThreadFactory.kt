package org.jitsi.jibri.util

import java.util.concurrent.ThreadFactory

class NameableThreadFactory(private val name: String) : ThreadFactory {
    override fun newThread(r: Runnable?): Thread {
        return Thread(r, name)
    }
}