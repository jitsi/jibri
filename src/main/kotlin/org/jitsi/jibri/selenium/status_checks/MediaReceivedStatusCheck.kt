package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Clock
import java.time.Duration
import java.util.logging.Logger

/**
 * Verify that the Jibri web client is receiving media from the other participants
 * in the call, and, if we don't receive any media for a certain time, return
 * [SeleniumEvent.NoMediaReceived].  There's a caveat that we'll say longer if there are participants
 * in the call but they are audio and video muted; eventually even in this scenario we'll
 * timeout, but return [SeleniumEvent.CallEmpty].
 */
class MediaReceivedStatusCheck(
    private val logger: Logger,
    private val clock: Clock = Clock.systemDefaultZone()
) : CallStatusCheck {
    // The last timestamp where we saw non-zero media.  We default with the
    // assumption we're receiving media.
    private var timeOfLastMedia = clock.instant()
    // The timestamp at which we last saw that all clients transitioned to muted
    private val clientsAllMutedTransitionTime = StateTransitionTimeTracker(clock)

    override fun run(callPage: CallPage): SeleniumEvent? {
        val now = clock.instant()
        val bitrates = callPage.getBitrates()
        // getNumParticipants includes Jibri, so subtract 1
        val allClientsMuted = callPage.numRemoteParticipantsMuted() == (callPage.getNumParticipants() - 1)
        logger.info("Jibri client receive bitrates: $bitrates, all clients muted? $allClientsMuted")
        clientsAllMutedTransitionTime.maybeUpdate(allClientsMuted)
        val downloadBitrate = bitrates.getOrDefault("download", 0L) as Long
        // If all clients are muted, register it as 'receiving media': that way when clients unmute
        // we'll get the full NO_MEDIA_TIMEOUT duration before timing out due to lack of media
        if (downloadBitrate != 0L || allClientsMuted) {
            timeOfLastMedia = now
        }
        val timeSinceLastMedia = Duration.between(timeOfLastMedia, now)

        // There are a couple possible outcomes here:
        // 1) All clients are muted, but have been muted for longer than ALL_MUTED_TIMEOUT so
        //     we'll exit the call gracefully (CallEmpty)
        // 2) No media has flowed for longer than NO_MEDIA_TIMEOUT and all clients are not
        //     muted so we'll exit with an error (NoMediaReceived)
        // 3) If neither of the above are true, we're fine and no event has occurred
        return when {
            clientsAllMutedTransitionTime.exceededTimeout(ALL_MUTED_TIMEOUT) -> SeleniumEvent.CallEmpty
            timeSinceLastMedia > NO_MEDIA_TIMEOUT && !allClientsMuted -> SeleniumEvent.NoMediaReceived
            else -> null
        }
    }

    companion object {
        /**
         * How long we'll stay in the call if we're not receiving any incoming media (assuming all participants
         * are not muted)
         */
        private val NO_MEDIA_TIMEOUT: Duration = Duration.ofSeconds(30)

        /**
         * How long we'll stay in the call if all participants are muted
         */
        private val ALL_MUTED_TIMEOUT: Duration = Duration.ofMinutes(10)
    }
}
