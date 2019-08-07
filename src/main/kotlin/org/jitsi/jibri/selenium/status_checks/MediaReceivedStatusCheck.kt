package org.jitsi.jibri.selenium.status_checks

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
    // The timestamp at which we last saw that all clients were muted
    private var firstTimeAllClientsMuted: Instant? = null

    override fun run(callPage: CallPage): SeleniumEvent? {
        val now = clock.instant()
        val bitrates = callPage.getBitrates()
        // getNumParticipants includes Jibri, so subtract 1
        val allClientsMuted = callPage.numRemoteParticipantsMuted() == (callPage.getNumParticipants() - 1)
        logger.info("Jibri client receive bitrates: $bitrates, all clients muted? $allClientsMuted")
        if (allClientsMuted && firstTimeAllClientsMuted == null) {
            firstTimeAllClientsMuted = now
        } else if (!allClientsMuted) {
            firstTimeAllClientsMuted = null
        }
        // There are a couple possible outcomes here:
        // 1) downloadBitrate == 0 and not all the participants in the call are muted (meaning
        //     we *should* be receiving media) --> Fire SeleniumEvent.NoMediaReceived
        // 2) downloadBitrate == 0 and all clients are muted, BUT this has been the case for too
        //     long and we're going to timeout --> Fire SeleniumEvent.CallEmpty
        val downloadBitrate = bitrates.getOrDefault("download", 0L) as Long
        if (downloadBitrate != 0L) {
            timeOfLastMedia = now
        }
        val timeSinceLastMedia = Duration.between(timeOfLastMedia, now)

        if (allClientsMuted) {
            val timeSinceAllClientsMuted = Duration.between(firstTimeAllClientsMuted!!, now)
            return if (timeSinceAllClientsMuted > ALL_MUTED_TIMEOUT) {
                SeleniumEvent.CallEmpty
            } else {
                null
            }
        } else if (timeSinceLastMedia > NO_MEDIA_TIMEOUT) {
            return SeleniumEvent.NoMediaReceived
        }

        return null
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
