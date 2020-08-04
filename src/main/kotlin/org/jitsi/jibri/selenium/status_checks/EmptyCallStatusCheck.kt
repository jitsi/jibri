package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Clock
import java.time.Duration
import java.util.logging.Logger

/**
 * Verify that there are other participants in the call; if the call is
 * empty for more than [callEmptyTimeout], then return [SeleniumEvent.CallEmpty].
 *
 * NOTE: this class doesn't perform the check automatically, it will only
 * check for call empty state when [run] is called.
 */
class EmptyCallStatusCheck(
    private val logger: Logger,
    private val callEmptyTimeout: Duration = DEFAULT_CALL_EMPTY_TIMEOUT,
    private val clock: Clock = Clock.systemUTC()
) : CallStatusCheck {
    init {
        logger.info("Starting empty call check with a timeout of $callEmptyTimeout")
    }
    // The timestamp at which we last saw the call transition from
    // non-empty to empty
    private val callWentEmptyTime = StateTransitionTimeTracker(clock)
    override fun run(callPage: CallPage): SeleniumEvent? {
        val now = clock.instant()
        callWentEmptyTime.maybeUpdate(callPage.isCallEmpty())

        return when (callWentEmptyTime.exceededTimeout(callEmptyTimeout)) {
            true -> {
                logger.info("Call has been empty since " +
                    "${callWentEmptyTime.timestampTransitionOccured} " +
                    "(${Duration.between(callWentEmptyTime.timestampTransitionOccured, now)} ago). " +
                    "Returning CallEmpty event")
                SeleniumEvent.CallEmpty
            }
            false -> null
        }
    }

    // <= 1 since the count will include jibri itself
    private fun CallPage.isCallEmpty() = getNumParticipants() <= 1

    companion object {
        val DEFAULT_CALL_EMPTY_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
