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

package org.jitsi.jibri.selenium.status_checks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.jitsi.jibri.helpers.FakeClock
import org.jitsi.jibri.helpers.minutes
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Duration
import java.util.logging.Logger

internal class EmptyCallStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val clock: FakeClock = spyk()
    private val callPage: CallPage = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private val check = EmptyCallStatusCheck(logger, clock = clock)

    init {
        context("when the call was always empty") {
            every { callPage.getNumParticipants() } returns 1
            context("the check") {
                should("return empty after the timeout") {
                    check.run(callPage) shouldBe null
                    clock.elapse(15.seconds)
                    check.run(callPage) shouldBe null
                    clock.elapse(20.seconds)
                    check.run(callPage) shouldBe SeleniumEvent.CallEmpty
                }
            }
        }
        context("when the call has participants") {
            every { callPage.getNumParticipants() } returns 3
            clock.elapse(5.minutes)
            context("the check") {
                should("never return empty") {
                    check.run(callPage) shouldBe null
                    clock.elapse(10.minutes)
                    check.run(callPage) shouldBe null
                }
            }
            context("and then goes empty") {
                every { callPage.getNumParticipants() } returns 1
                clock.elapse(20.seconds)
                context("the check") {
                    should("return empty after the timeout") {
                        check.run(callPage) shouldBe null
                        clock.elapse(31.seconds)
                        check.run(callPage) shouldBe SeleniumEvent.CallEmpty
                    }
                }
                context("and then has participants again") {
                    every { callPage.getNumParticipants() } returns 3
                    // Some time passed and the check ran once with no participants
                    clock.elapse(30.seconds)
                    context("the check") {
                        should("never return empty") {
                            check.run(callPage) shouldBe null
                            clock.elapse(10.minutes)
                            check.run(callPage) shouldBe null
                        }
                    }
                }
            }
        }
        context("when a custom timeout is passed") {
            every { callPage.getNumParticipants() } returns 1
            val customTimeoutCheck = EmptyCallStatusCheck(logger, Duration.ofMinutes(10), clock)
            context("the check") {
                should("return empty after the timeout") {
                    customTimeoutCheck.run(callPage) shouldBe null
                    clock.elapse(15.seconds)
                    customTimeoutCheck.run(callPage) shouldBe null
                    clock.elapse(45.seconds)
                    customTimeoutCheck.run(callPage) shouldBe null
                    clock.elapse(8.minutes)
                    customTimeoutCheck.run(callPage) shouldBe null
                    clock.elapse(61.seconds)
                    customTimeoutCheck.run(callPage) shouldBe SeleniumEvent.CallEmpty
                }
            }
        }
    }
}
