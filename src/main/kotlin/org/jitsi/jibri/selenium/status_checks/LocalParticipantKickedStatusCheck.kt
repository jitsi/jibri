package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import java.time.Clock
import java.time.Duration

class LocalParticipantKickedStatusCheck(
    parentLogger: Logger,
    private val clock: Clock = Clock.systemUTC()
) : CallStatusCheck {
    private val logger = createChildLogger(parentLogger)

    init {
        logger.info("Starting local participant kicked out call check")
    }

    // The timestamp at which we last saw the call transition from
    // joined to kicked out
    private val localParticipantKickedTime = StateTransitionTimeTracker(clock)
    override fun run(callPage: CallPage): SeleniumEvent? {
        val now = clock.instant()
        localParticipantKickedTime.maybeUpdate(callPage.isLocalParticipantKicked())

        return when (localParticipantKickedTime.timestampTransitionOccured != null) {
            true -> {
                logger.info(
                    "Local participant has been kicked since " +
                            "${localParticipantKickedTime.timestampTransitionOccured} " +
                            "(${Duration.between(localParticipantKickedTime.timestampTransitionOccured, now)} ago). " +
                            "Returning LocalParticipantKicked event"
                )
                SeleniumEvent.LocalParticipantKicked
            }
            false -> null
        }
    }
}
