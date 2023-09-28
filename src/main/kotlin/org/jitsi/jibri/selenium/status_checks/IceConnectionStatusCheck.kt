package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import java.time.Clock
import java.time.Duration

/**
 * A check for the ICE connection. If ICE is not in the "connected" state for more than [iceConnectionTimeout] then a
 * [SeleniumEvent.IceFailedEvent] is fired.
 */
class IceConnectionStatusCheck(
    parentLogger: Logger,
    private val clock: Clock = Clock.systemDefaultZone()
) : CallStatusCheck {
    private val logger = createChildLogger(parentLogger)

    // The last timestamp when ICE was connected. Initialized to give the same timeout for the initial connection.
    private var timeOfLastSuccess = clock.instant()

    override fun run(callPage: CallPage): SeleniumEvent? {
        val now = clock.instant()

        if (callPage.isCallEmpty() || callPage.isIceConnected()) {
            // If there are no other participants we don't expect to have an ICE connection.
            timeOfLastSuccess = now
            return null
        }

        if (Duration.between(timeOfLastSuccess, now) > iceConnectionTimeout) {
            logger.warn("ICE has failed and not recovered in $iceConnectionTimeout.")
            return SeleniumEvent.IceFailedEvent
        }
        return null
    }

    companion object {
        val iceConnectionTimeout: Duration by config {
            "jibri.call-status-checks.ice-connection-timeout".from(Config.configSource)
        }
    }
}
