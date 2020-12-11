/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.jibri.selenium.statuschecks

import org.jitsi.jibri.ClientMuteLimitExceeded
import org.jitsi.jibri.NoMediaReceivedException
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.util.StateTransitionTimeTracker
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import java.time.Clock
import java.time.Duration

class MediaReceivedCheck(
    parentLogger: Logger,
    private val clock: Clock = Clock.systemDefaultZone()
) : CallCheck {
    private val logger = createChildLogger(parentLogger)
    // The last timestamp where we saw non-zero media.  We default with the
    // assumption we're receiving media.
    private var timeOfLastMedia = clock.instant()

    // The timestamp at which we last saw that all clients transitioned to muted
    private val clientsAllMutedTransitionTime = StateTransitionTimeTracker(clock)

    override fun runCheck(callPage: CallPage) {
        val now = clock.instant()
        val bitrates = callPage.getBitrates()
        // getNumParticipants includes Jibri, so subtract 1
        val numParticipants = callPage.getNumParticipants() - 1
        val numMutedParticipants = callPage.numRemoteParticipantsMuted()
        val numJigasiParticipants = callPage.numRemoteParticipantsJigasi()
        // We don't get any mute state for Jigasi participants, so to prevent timing out when only Jigasi participants
        // may be speaking, always count them as "muted"
        val allClientsMuted = (numMutedParticipants + numJigasiParticipants) == numParticipants
        logger.info(
            "Jibri client receive bitrates: $bitrates, num participants: $numParticipants, " +
                "numMutedParticipants: $numMutedParticipants, numJigasis: $numJigasiParticipants, " +
                "all clients muted? $allClientsMuted"
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
        when {
            clientsAllMutedTransitionTime.exceededTimeout(allMutedTimeout) -> throw ClientMuteLimitExceeded
            timeSinceLastMedia > noMediaTimeout && !allClientsMuted -> throw NoMediaReceivedException
        }
    }

    companion object {
        /**
         * How long we'll stay in the call if we're not receiving any incoming media (assuming all participants
         * are not muted)
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
