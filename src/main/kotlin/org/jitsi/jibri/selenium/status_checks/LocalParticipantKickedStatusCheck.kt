package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger

class LocalParticipantKickedStatusCheck(
    parentLogger: Logger
) : CallStatusCheck {
    private val logger = createChildLogger(parentLogger)

    init {
        logger.info("Starting local participant kicked out call check")
    }

    override fun run(callPage: CallPage): SeleniumEvent? {
        return if (callPage.isLocalParticipantKicked()) {
            logger.info("Local participant was kicked, returning LocalParticipantKicked event")
            SeleniumEvent.LocalParticipantKicked
        } else {
            null
        }
    }
}
