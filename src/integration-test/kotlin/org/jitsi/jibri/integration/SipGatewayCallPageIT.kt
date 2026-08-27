/*
 * Copyright @ 2026 - present 8x8, Inc.
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

package org.jitsi.jibri.integration

import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * The [org.jitsi.jibri.selenium.pageobjects.CallPage] behaviour jibri only uses when it is gatewaying a SIP
 * call: unlike a recording jibri, a SIP jibri sends media of its own, can be told to mute and unmute over
 * DTMF, and raises its hand when it is not allowed to unmute.
 *
 * The rest of the interface is covered by [CallPageIT].
 */
class SipGatewayCallPageIT : ShouldSpec({
    CallPageImpl.entries.forEach { impl ->
        context(impl.name) {
            should("join muted and unmute both audio and video") {
                val roomName = TestDeployment.randomRoomName("sipunmute")
                MeetParticipant("participant-1").use { participant ->
                    participant.join(roomName)

                    JibriBrowser(impl).use { jibri ->
                        jibri.joinOrSipFail(roomName)
                        jibri.page.isLocalAudioMuted() shouldBe true
                        jibri.page.isLocalVideoMuted() shouldBe true

                        jibri.page.unmute() shouldBe true
                        await(description = "jibri's own audio to be unmuted") {
                            jibri.page.isLocalAudioMuted() shouldBe false
                        }
                        await(description = "jibri's own video to be unmuted") {
                            jibri.page.isLocalVideoMuted() shouldBe false
                        }

                        await(description = "the participant to see jibri unmuted") {
                            val jibriAsSeenByParticipant = participant.onlyRemoteParticipant()
                            jibriAsSeenByParticipant["audioMuted"] shouldBe false
                            jibriAsSeenByParticipant["videoMuted"] shouldBe false
                        }
                    }
                }
            }

            should("toggle its own audio and video mute") {
                val roomName = TestDeployment.randomRoomName("siptoggle")
                MeetParticipant("participant-1").use { participant ->
                    participant.join(roomName)

                    JibriBrowser(impl).use { jibri ->
                        jibri.joinOrSipFail(roomName)
                        jibri.page.unmute() shouldBe true
                        await(description = "jibri to start out unmuted") {
                            jibri.page.isLocalAudioMuted() shouldBe false
                            jibri.page.isLocalVideoMuted() shouldBe false
                        }

                        // This is what handleDtmfStar6 and handleDtmfStar7 do when the SIP client presses
                        // *6 or *7, and both report what happened in the string they return.
                        jibri.page.toggleAudioMute().toString() shouldStartWith "ok"
                        await(description = "jibri's audio to be muted") {
                            jibri.page.isLocalAudioMuted() shouldBe true
                        }
                        jibri.page.toggleAudioMute().toString() shouldStartWith "ok"
                        await(description = "jibri's audio to be unmuted again") {
                            jibri.page.isLocalAudioMuted() shouldBe false
                        }

                        jibri.page.toggleVideoMute().toString() shouldStartWith "ok"
                        await(description = "jibri's video to be muted") {
                            jibri.page.isLocalVideoMuted() shouldBe true
                        }
                        jibri.page.toggleVideoMute().toString() shouldStartWith "ok"
                        await(description = "jibri's video to be unmuted again") {
                            jibri.page.isLocalVideoMuted() shouldBe false
                        }
                    }
                }
            }

            should("raise and lower its hand") {
                val roomName = TestDeployment.randomRoomName("siphand")
                MeetParticipant("participant-1").use { participant ->
                    participant.join(roomName)

                    JibriBrowser(impl).use { jibri ->
                        jibri.joinOrSipFail(roomName)
                        await(description = "the participant to see jibri") {
                            participant.remoteParticipants().size shouldBe 1
                        }

                        jibri.page.raiseHand() shouldBe true
                        await(description = "the participant to see jibri's hand raised") {
                            participant.onlyRemoteParticipant()["raisedHand"] shouldBe true
                        }

                        jibri.page.raiseHand() shouldBe true
                        await(description = "the participant to see jibri's hand lowered") {
                            participant.onlyRemoteParticipant()["raisedHand"] shouldBe false
                        }
                    }
                }
            }

            should("report when AV moderation is stopping it from unmuting") {
                val roomName = TestDeployment.randomRoomName("sipavmod")
                // The first participant in the room is its moderator, and only a moderator can turn AV
                // moderation on.
                MeetParticipant("moderator").use { moderator ->
                    moderator.join(roomName)
                    moderator.setAvModerationEnabled("audio", true)
                    moderator.setAvModerationEnabled("video", true)

                    JibriBrowser(impl).use { jibri ->
                        jibri.joinOrSipFail(roomName)

                        // This is what makes handleDtmfStar6 raise a hand instead of trying to unmute.
                        await(description = "jibri to see that it is force muted") {
                            jibri.page.isAudioForceMuted() shouldBe true
                            jibri.page.isVideoForceMuted() shouldBe true
                        }

                        moderator.approveEveryoneElse("audio")
                        await(description = "jibri to see that it may unmute audio") {
                            jibri.page.isAudioForceMuted() shouldBe false
                        }
                        jibri.page.isVideoForceMuted() shouldBe true

                        moderator.setAvModerationEnabled("video", false)
                        await(description = "jibri to see that video moderation is off") {
                            jibri.page.isVideoForceMuted() shouldBe false
                        }
                    }
                }
            }
        }
    }
})

/** Joins [roomName] the way a SIP gateway jibri does, failing with the browser log if it does not work. */
private fun JibriBrowser.joinOrSipFail(roomName: String) {
    if (!join(roomName, JoinMode.SIP_GATEWAY)) {
        fail("Failed to join $roomName as a SIP gateway. Browser log:\n${browserLogs().joinToString("\n")}")
    }
}
