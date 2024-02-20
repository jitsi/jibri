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
 * Verify that the Jibri web client is receiving media from the other participants. There's two different cases:
 * 1. If some participants have audio and/or video enabled (as advertised in signaling). In this case, if jibri
 * receives no audio or video for [noMediaTimeout] it will fire a [SeleniumEvent.NoMediaReceived]. This should not
 * happen in normal circumstances because if anyone is sending media, it should be forwarded to and received by jibri,
 * and we have a separate [IceConnectionStatusCheck] for the connection to the bridge. The check is here as a safety
 * net.
 * 2. No participant has audio or video enabled (as advertised in signaling). In this case we timeout after
 * [allMutedTimeout] and fire a [SeleniumEvent.CallEmpty] to avoid wasting resources.
 */
class MediaReceivedStatusCheck(
    parentLogger: Logger,
    private val clock: Clock = Clock.systemDefaultZone()
) : CallStatusCheck {
    private val logger = createChildLogger(parentLogger)

    // The last timestamp where we saw non-zero media.  We default with the
    // assumption we're receiving media.
    private var timeOfLastMedia = clock.instant()

    // The timestamp at which we last saw that all clients transitioned to muted
    private val clientsAllMutedTransitionTime = StateTransitionTimeTracker(clock)

    override fun run(callPage: CallPage): SeleniumEvent? {
        val now = clock.instant()
        val bitrates = callPage.getBitrates()
        // getNumParticipants includes Jibri, so subtract 1
        val numParticipants = callPage.getNumParticipants() - 1
        val numMutedParticipants = callPage.numRemoteParticipantsMuted()
        val numJigasiParticipants = callPage.numRemoteParticipantsJigasi()
        val numHiddenParticipants = callPage.numHiddenParticipants()
        // We don't get any mute state for Jigasi participants, so to prevent timing out when only Jigasi participants
        // may be speaking, always count them as "muted"
        val allClientsMuted = (numMutedParticipants + numJigasiParticipants) == numParticipants
        logger.info(
            "Jibri client receive bitrates: $bitrates, num participants: $numParticipants, " +
                "numMutedParticipants: $numMutedParticipants, numJigasis: $numJigasiParticipants, " +
                "numHiddenParticipants: $numHiddenParticipants, all clients muted? $allClientsMuted"
        )
        clientsAllMutedTransitionTime.maybeUpdate(allClientsMuted)
        val downloadBitrate = bitrates.getOrDefault("download", 0L) as Long
        // If all clients are muted, register it as 'receiving media': that way when clients unmute
        // we'll get the full noMediaTimeout duration before timing out due to lack of media.
        if (downloadBitrate != 0L || allClientsMuted) {
            timeOfLastMedia = now
        }
        val timeSinceLastMedia = Duration.between(timeOfLastMedia, now)

        // There are a couple possible outcomes here:
        // 1) All clients are muted, but have been muted for longer than allMutedTimeout so
        //     we'll exit the call gracefully (CallEmpty)
        // 2) No media has flowed for longer than noMediaTimeout and all clients are not
        //     muted so we'll exit with an error (NoMediaReceived)
        // 3) If neither of the above are true, we're fine and no event has occurred
        return when {
            clientsAllMutedTransitionTime.exceededTimeout(allMutedTimeout) -> SeleniumEvent.CallEmpty
            timeSinceLastMedia > noMediaTimeout && !allClientsMuted -> SeleniumEvent.NoMediaReceived
            else -> null
        }
    }

    companion object {
        /**
         * How long we'll stay in the call if we're not receiving any incoming media (assuming not all participants
         * not muted). This should be long enough to allow for a participant losing their connection and re-connecting
         * without restarting the recording.
         */
        val noMediaTimeout: Duration by config {
            "jibri.call-status-checks.no-media-timeout".from(Config.configSource)
        }

        /**
         * How long we'll stay in the call if all participants are muted
         */
        val allMutedTimeout: Duration by config {
            "jibri.call-status-checks.all-muted-timeout".from(Config.configSource)
        }
    }
}
