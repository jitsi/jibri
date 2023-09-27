package org.jitsi.jibri.helpers

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.jitsi.jibri.error.JibriError
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.status.ComponentState

class SeleniumMockHelper {
    private val eventHandlers = mutableListOf<(ComponentState) -> Boolean>()

    val mock: JibriSelenium = mockk(relaxed = true) {
        every { addTemporaryHandler(capture(eventHandlers)) } just Runs
        every { addStatusHandler(captureLambda()) } answers {
            // This behavior mimics what's done in StatusPublisher#addStatusHandler
            eventHandlers.add {
                lambda<(ComponentState) -> Unit>().captured(it)
                true
            }
        }
    }

    fun startSuccessfully() {
        eventHandlers.forEach { it(ComponentState.Running) }
    }

    fun error(error: JibriError) {
        eventHandlers.forEach { it(ComponentState.Error(error)) }
    }
}
