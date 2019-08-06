package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage

/**
 * [CallStatusCheck]s are executed periodically and perform checks via javascripe on the call page to make sure
 * everything is running correctly.
 */
interface CallStatusCheck {
    /**
     * Run a check via [callPage], return a [SeleniumEvent] if something notable has been detected (i.e. an error),
     * null if whatever the check is looking for was fine.
     */
    fun run(callPage: CallPage): SeleniumEvent?
}
