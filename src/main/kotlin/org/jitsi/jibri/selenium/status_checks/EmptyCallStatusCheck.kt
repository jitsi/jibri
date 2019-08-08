package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage

/**
 * Verify that there are other participants in the call; if there are none
 * then return [SeleniumEvent.CallEmpty].
 */
class EmptyCallStatusCheck : CallStatusCheck {
    private var numTimesEmpty = 0
    override fun run(callPage: CallPage): SeleniumEvent? {
        // >1 since the count will include jibri itself
        if (callPage.getNumParticipants() > 1) {
            numTimesEmpty = 0
        } else {
            numTimesEmpty++
        }
        if (numTimesEmpty >= 2) {
            return SeleniumEvent.CallEmpty
        }
        return null
    }
}
