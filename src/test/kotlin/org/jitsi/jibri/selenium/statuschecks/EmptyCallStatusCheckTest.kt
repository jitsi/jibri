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
import org.jitsi.jibri.EmptyCallException
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.test.time.FakeClock
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.mins
import org.jitsi.utils.secs
import java.time.Duration

internal class EmptyCallStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val clock = FakeClock()
    private val callPage: CallPage = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private val check = EmptyCallCheck(logger, clock = clock)

    init {
        context("when the call was always empty") {
            every { callPage.getNumParticipants() } returns 1
            context("the check") {
                should("return empty after the timeout") {
                    check.runCheck(callPage)
                    clock.elapse(15.secs)
                    check.runCheck(callPage)
                    clock.elapse(20.secs)
                    shouldThrow<EmptyCallException> {
                        check.runCheck(callPage)
                    }
                }
            }
        }
        context("when the call has participants") {
            every { callPage.getNumParticipants() } returns 3
            clock.elapse(5.mins)
            context("the check") {
                should("never return empty") {
                    check.runCheck(callPage)
                    clock.elapse(10.mins)
                    check.runCheck(callPage)
                }
            }
            context("and then goes empty") {
                every { callPage.getNumParticipants() } returns 1
                clock.elapse(20.secs)
                context("the check") {
                    should("return empty after the timeout") {
                        check.runCheck(callPage)
                        clock.elapse(31.secs)
                        shouldThrow<EmptyCallException> {
                            check.runCheck(callPage)
                        }
                    }
                }
                context("and then has participants again") {
                    every { callPage.getNumParticipants() } returns 3
                    // Some time passed and the check ran once with no participants
                    clock.elapse(30.secs)
                    context("the check") {
                        should("never return empty") {
                            check.runCheck(callPage)
                            clock.elapse(10.mins)
                            check.runCheck(callPage)
                        }
                    }
                }
            }
        }
        context("when a custom timeout is passed") {
            every { callPage.getNumParticipants() } returns 1
            val customTimeoutCheck = EmptyCallCheck(logger, Duration.ofMinutes(10), clock)
            context("the check") {
                should("return empty after the timeout") {
                    customTimeoutCheck.runCheck(callPage)
                    clock.elapse(15.secs)
                    customTimeoutCheck.runCheck(callPage)
                    clock.elapse(45.secs)
                    customTimeoutCheck.runCheck(callPage)
                    clock.elapse(8.mins)
                    customTimeoutCheck.runCheck(callPage)
                    clock.elapse(61.secs)
                    shouldThrow<EmptyCallException> {
                        customTimeoutCheck.runCheck(callPage)
                    }
                }
            }
        }
    }
}
