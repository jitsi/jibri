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
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * Runs jibri's [org.jitsi.jibri.selenium.pageobjects.CallPage] implementations against a real
 * docker-jitsi-meet deployment, with real participants in the conference, joined the way jibri joins when
 * it is recording.  Both implementations are put through the same scenarios, because jibri is expected to
 * behave identically whichever one it uses.
 *
 * The scenarios jibri joins as a SIP gateway for are in [SipGatewayCallPageIT].
 *
 * These tests need a deployment: see resources/integration-tests/deployment.sh.
 */
class CallPageIT : ShouldSpec({
    CallPageImpl.entries.forEach { impl ->
        context(impl.name) {
            should("join a conference, then see participants join and leave it") {
                val roomName = TestDeployment.randomRoomName("jibrijoin")
                JibriBrowser(impl).use { jibri ->
                    jibri.joinOrFail(roomName)
                    jibri.page.injectParticipantTrackerScript() shouldBe true

                    jibri.page.getNumParticipants() shouldBe 1
                    jibri.page.isCallEmpty() shouldBe true

                    MeetParticipant("participant-1").use { first ->
                        first.join(roomName)
                        await(description = "jibri to see the first participant") {
                            jibri.page.getNumParticipants() shouldBe 2
                        }
                        jibri.page.isCallEmpty() shouldBe false

                        MeetParticipant("participant-2").use { second ->
                            second.join(roomName)
                            await(description = "jibri to see the second participant") {
                                jibri.page.getNumParticipants() shouldBe 3
                            }
                            // Nothing in this conference is hidden from the recorder, and nobody dialed in.
                            jibri.page.numHiddenParticipants() shouldBe 0
                            jibri.page.numRemoteParticipantsJigasi() shouldBe 0
                        }

                        await(description = "jibri to see the second participant leave") {
                            jibri.page.getNumParticipants() shouldBe 2
                        }
                    }

                    await(description = "jibri to see the conference empty again") {
                        jibri.page.isCallEmpty() shouldBe true
                    }
                    jibri.page.leave() shouldBe true
                }
            }

            should("connect ICE and receive media") {
                val roomName = TestDeployment.randomRoomName("jibrimedia")
                JibriBrowser(impl).use { jibri ->
                    jibri.joinOrFail(roomName)

                    MeetParticipant("participant-1").use { participant ->
                        participant.join(roomName)
                        await(description = "jibri to see the participant") {
                            jibri.page.getNumParticipants() shouldBe 2
                        }

                        await(description = "jibri's ICE connection to come up") {
                            jibri.page.isIceConnected() shouldBe true
                        }

                        // The bitrates drive jibri's media-received health check, so both the per-media
                        // entries and the totals have to be there.
                        await(description = "jibri to report the bitrates it is receiving", timeout = 60.seconds) {
                            val bitrates = jibri.page.getBitrates()
                            bitrates.keys shouldContainAll setOf("audio", "video", "download", "upload")
                            (bitrates["download"] as Number).toInt() shouldBeGreaterThan 0
                        }
                    }
                }
            }

            should("report which remote participants are muted") {
                val roomName = TestDeployment.randomRoomName("jibrimute")
                JibriBrowser(impl).use { jibri ->
                    jibri.joinOrFail(roomName)

                    MeetParticipant("participant-1").use { participant ->
                        participant.join(roomName)
                        await(description = "jibri to see the participant") {
                            jibri.page.getNumParticipants() shouldBe 2
                        }

                        // The participant joins unmuted, so jibri should not count it as muted.
                        await(description = "jibri to see the participant unmuted") {
                            jibri.page.numRemoteParticipantsMuted() shouldBe 0
                        }

                        participant.setMuted(true)
                        await(description = "jibri to see the participant muted") {
                            jibri.page.numRemoteParticipantsMuted() shouldBe 1
                        }

                        participant.setMuted(false)
                        await(description = "jibri to see the participant unmuted again") {
                            jibri.page.numRemoteParticipantsMuted() shouldBe 0
                        }
                    }
                }
            }

            should("count jigasi participants and leave them out of the muted count") {
                val roomName = TestDeployment.randomRoomName("jibrijigasi")
                MeetParticipant("jigasi-1").use { jigasi ->
                    jigasi.join(roomName)
                    // Has to happen before jibri joins: features are only queried when a participant
                    // is first seen.
                    jigasi.advertiseFeature(JIGASI_FEATURE)

                    MeetParticipant("participant-1").use { participant ->
                        participant.join(roomName)

                        JibriBrowser(impl).use { jibri ->
                            jibri.joinOrFail(roomName)
                            await(description = "jibri to see both participants") {
                                jibri.page.getNumParticipants() shouldBe 3
                            }
                            await(description = "jibri to recognise the jigasi participant") {
                                jibri.page.numRemoteParticipantsJigasi() shouldBe 1
                            }

                            // A jigasi participant can report itself muted while still sending audio from
                            // the SIP side, so jibri deliberately ignores it when counting muted people.
                            jigasi.setMuted(true)
                            await(description = "jibri to ignore the muted jigasi participant") {
                                jibri.page.numRemoteParticipantsMuted() shouldBe 0
                            }

                            participant.setMuted(true)
                            await(description = "jibri to count the muted participant") {
                                jibri.page.numRemoteParticipantsMuted() shouldBe 1
                            }
                        }
                    }
                }
            }

            should("notice when it is kicked out of the conference") {
                val roomName = TestDeployment.randomRoomName("jibrikick")
                // The first participant in the room is its moderator, and only a moderator can kick.
                MeetParticipant("moderator").use { moderator ->
                    moderator.join(roomName)

                    JibriBrowser(impl).use { jibri ->
                        jibri.joinOrFail(roomName)
                        jibri.page.injectLocalParticipantTrackerScript() shouldBe true
                        jibri.page.isLocalParticipantKicked() shouldBe false

                        moderator.kickEveryoneElse()
                        await(description = "jibri to notice it was kicked") {
                            jibri.page.isLocalParticipantKicked() shouldBe true
                        }
                    }
                }
            }

            should("report the identities of the participants who took part") {
                val roomName = TestDeployment.randomRoomName("jibriidentity")
                JibriBrowser(impl).use { jibri ->
                    jibri.joinOrFail(roomName)
                    jibri.page.injectParticipantTrackerScript() shouldBe true

                    val user = mapOf(
                        "id" to "user-1",
                        "name" to "First Participant",
                        "email" to "first@example.com"
                    )
                    MeetParticipant("participant-1", user).use { participant ->
                        participant.join(roomName)
                        await(description = "jibri to see the participant") {
                            jibri.page.getNumParticipants() shouldBe 2
                        }

                        // This is what jibri writes into the metadata.json of a finished recording.
                        await(description = "jibri to record the participant's identity") {
                            val identities = jibri.page.getParticipants()
                            identities.size shouldBe 1
                            val identity = identities.single()["user"] as Map<*, *>
                            identity["id"] shouldBe "user-1"
                            identity["email"] shouldBe "first@example.com"
                        }
                    }
                }
            }

            should("recognise participants that are hidden from the recorder") {
                val roomName = TestDeployment.randomRoomName("jibrihidden")
                JibriBrowser(impl).use { jibri ->
                    jibri.joinOrFail(roomName)

                    val hiddenUser = mapOf(
                        "id" to "hidden-1",
                        "name" to "Hidden Participant",
                        "hidden-from-recorder" to "true"
                    )
                    MeetParticipant("hidden-1", hiddenUser).use { hidden ->
                        hidden.join(roomName)
                        MeetParticipant("participant-1").use { visible ->
                            visible.join(roomName)

                            await(description = "jibri to see the hidden participant as hidden") {
                                jibri.page.numHiddenParticipants() shouldBe 1
                            }
                        }
                    }
                    // getNumParticipants is deliberately not asserted here: AppCallPage leaves
                    // participants hidden from the recorder out of it, ExternalAPIPage counts them,
                    // because the External API's getRoomsInfo only filters on isHidden().  That makes
                    // isCallEmpty disagree between the two for a conference with nobody but hidden
                    // participants left in it.  Tighten this once they agree.
                }
            }

            should("publish the properties the recording services put in its presence") {
                val roomName = TestDeployment.randomRoomName("jibripresence")
                MeetParticipant("participant-1").use { participant ->
                    participant.join(roomName)

                    JibriBrowser(impl).use { jibri ->
                        jibri.joinOrFail(roomName)

                        // What FileRecordingJibriService and StreamingJibriService announce once they start.
                        jibri.page.setParticipantProperties(
                            mapOf("session_id" to "test-session-id", "mode" to "file")
                        ) shouldBe true
                        await(description = "the participant to see jibri's recording properties") {
                            participant.remotePresenceValue("session_id") shouldBe "test-session-id"
                            participant.remotePresenceValue("mode") shouldBe "file"
                        }

                        jibri.page.addToPresence("live-stream-view-url", "https://example.com/live") shouldBe true
                        jibri.page.sendPresence() shouldBe true
                        await(description = "the participant to see jibri's stream url") {
                            participant.remotePresenceValue("live-stream-view-url") shouldBe
                                "https://example.com/live"
                        }
                    }
                }
            }
        }
    }
})

/**
 * Joins [roomName], failing the test with the browser log attached if the join did not succeed: without it
 * a failed join says nothing about why the page could not reach the deployment.
 */
private fun JibriBrowser.joinOrFail(
    roomName: String,
    mode: JoinMode = JoinMode.RECORDER,
    extraUrlParams: List<String> = listOf()
) {
    if (!join(roomName, mode, extraUrlParams)) {
        fail("Failed to join $roomName as $mode. Browser log:\n${browserLogs().joinToString("\n")}")
    }
}
