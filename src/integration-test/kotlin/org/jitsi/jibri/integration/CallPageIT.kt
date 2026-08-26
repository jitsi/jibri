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

/**
 * Runs jibri's [org.jitsi.jibri.selenium.pageobjects.CallPage] implementations against a real
 * docker-jitsi-meet deployment, with real participants in the conference.  Both implementations are put
 * through the same scenarios, because jibri is expected to behave identically whichever one it uses.
 *
 * These tests need a deployment: see resources/integration-tests/deployment.sh.
 */
class CallPageIT : ShouldSpec({
    CallPageImpl.entries.forEach { impl ->
        context(impl.name) {
            should("join a conference, then see a participant join and leave it") {
                val roomName = TestDeployment.randomRoomName("jibrijoin")
                JibriBrowser(impl).use { jibri ->
                    jibri.joinOrFail(roomName)

                    jibri.page.getNumParticipants() shouldBe 1
                    jibri.page.isCallEmpty() shouldBe true

                    MeetParticipant("participant-1").use { participant ->
                        participant.join(roomName)

                        await(description = "jibri to see the participant") {
                            jibri.page.getNumParticipants() shouldBe 2
                        }
                        jibri.page.isCallEmpty() shouldBe false
                        // Nothing in this conference is hidden from the recorder, and nobody dialed in.
                        jibri.page.numHiddenParticipants() shouldBe 0
                        jibri.page.numRemoteParticipantsJigasi() shouldBe 0

                        await(description = "jibri's ICE connection to come up") {
                            jibri.page.isIceConnected() shouldBe true
                        }
                    }

                    await(description = "jibri to see the conference empty again") {
                        jibri.page.isCallEmpty() shouldBe true
                    }
                    jibri.page.leave() shouldBe true
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
        }
    }
})

/**
 * Joins [roomName], failing the test with the browser log attached if the join did not succeed: without it
 * a failed join says nothing about why the page could not reach the deployment.
 */
private fun JibriBrowser.joinOrFail(roomName: String) {
    if (!join(roomName)) {
        fail("Failed to join $roomName. Browser log:\n${browserLogs().joinToString("\n")}")
    }
}
