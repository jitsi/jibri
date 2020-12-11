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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import org.jitsi.jibri.ClientMuteLimitExceeded
import org.jitsi.jibri.NoMediaReceivedException
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.test.time.FakeClock
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.secs
import java.time.Duration

class MediaReceivedStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val clock = FakeClock()
    private val callPage: CallPage = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private val check = MediaReceivedCheck(logger, clock)

    init {
        every { callPage.getNumParticipants() } returns 4
        context("when media is flowing") {
            every { callPage.getBitrates() } returns mapOf("download" to 1024L)
            every { callPage.numRemoteParticipantsMuted() } returns 0
            every { callPage.numRemoteParticipantsJigasi() } returns 0
            should("not report any event") {
                repeat(10) {
                    check.runCheck(callPage)
                    clock.elapse(30.secs)
                }
            }
        }
        context("no media is flowing") {
            every { callPage.getBitrates() } returns mapOf("download" to 0L)
            context("but all participants are muted") {
                every { callPage.numRemoteParticipantsMuted() } returns 3
                every { callPage.numRemoteParticipantsJigasi() } returns 0
                check.runCheck(callPage)
                context("before the clients-muted timeout") {
                    clock.elapse(45.secs)
                    should("not report any event") {
                        check.runCheck(callPage)
                    }
                }
                context("and then when clients unmute after the no-media timeout has elapsed") {
                    // Go past NO_MEDIA_TIMEOUT, but everyone still muted
                    clock.elapse(35.secs)
                    every { callPage.numRemoteParticipantsMuted() } returns 3
                    every { callPage.numRemoteParticipantsJigasi() } returns 0
                    check.runCheck(callPage)
                    // Now go another second, but none are muted
                    clock.elapse(1.secs)
                    every { callPage.numRemoteParticipantsMuted() } returns 0
                    should("wait for the full no-media timeout duration before dropping") {
                        check.runCheck(callPage)
                    }
                }
                context("after the clients-muted timeout") {
                    clock.elapse(Duration.ofMinutes(15))
                    should("report the mute limit was exceeded") {
                        shouldThrow<ClientMuteLimitExceeded> {
                            check.runCheck(callPage)
                        }
                    }
                }
            }
            context("and not all participants are muted") {
                every { callPage.numRemoteParticipantsMuted() } returns 0
                every { callPage.numRemoteParticipantsJigasi() } returns 0
                context("before the no-media timeout") {
                    clock.elapse(15.secs)
                    should("not report any event") {
                        check.runCheck(callPage)
                    }
                    context("and media starts back up before the no-media timeout") {
                        every { callPage.getBitrates() } returns mapOf("download" to 1024L)
                        clock.elapse(Duration.ofMinutes(1))
                        should("not report any event") {
                            check.runCheck(callPage)
                        }
                    }
                }
                context("after the no-media timeout") {
                    clock.elapse(45.secs)
                    should("report a no media error") {
                        shouldThrow<NoMediaReceivedException> {
                            check.runCheck(callPage)
                        }
                    }
                }
            }
            context("and all participants are jigasis") {
                every { callPage.numRemoteParticipantsMuted() } returns 0
                every { callPage.numRemoteParticipantsJigasi() } returns 3
                should("not fire any event") {
                    check.runCheck(callPage)
                }
            }
            context("and some of the participants are jigasi") {
                every { callPage.numRemoteParticipantsMuted() } returns 1
                every { callPage.numRemoteParticipantsJigasi() } returns 2
                should("not fire any event") {
                }
            }
        }
    }
}
