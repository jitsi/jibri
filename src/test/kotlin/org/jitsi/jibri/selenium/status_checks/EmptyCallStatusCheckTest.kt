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

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.IsolationMode
import io.kotlintest.minutes
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.helpers.FakeClock
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Duration
import java.util.logging.Logger

internal class EmptyCallStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val clock: FakeClock = spy()
    private val callPage: CallPage = mock()
    private val logger: Logger = mock()

    private val check = EmptyCallStatusCheck(logger, clock = clock)

    init {
        "when the call was always empty" {
            whenever(callPage.getNumParticipants()).thenReturn(1)
            "the check" {
                should("return empty after the timeout") {
                    check.run(callPage) shouldBe null
                    clock.elapse(15.seconds)
                    check.run(callPage) shouldBe null
                    clock.elapse(20.seconds)
                    check.run(callPage) shouldBe SeleniumEvent.CallEmpty
                }
            }
        }
        "when the call has participants" {
            whenever(callPage.getNumParticipants()).thenReturn(3)
            clock.elapse(5.minutes)
            "the check" {
                should("never return empty") {
                    check.run(callPage) shouldBe null
                    clock.elapse(10.minutes)
                    check.run(callPage) shouldBe null
                }
            }
            "and then goes empty" {
                whenever(callPage.getNumParticipants()).thenReturn(1)
                clock.elapse(20.seconds)
                "the check" {
                    should("return empty after the timeout") {
                        check.run(callPage) shouldBe null
                        clock.elapse(11.seconds)
                        check.run(callPage) shouldBe SeleniumEvent.CallEmpty
                    }
                }
                "and then has participants again" {
                    whenever(callPage.getNumParticipants()).thenReturn(3)
                    // Some time passed and the check ran once with no participants
                    clock.elapse(30.seconds)
                    "the check" {
                        should("never return empty") {
                            check.run(callPage) shouldBe null
                            clock.elapse(10.minutes)
                            check.run(callPage) shouldBe null
                        }
                    }
                }
            }
        }
        "when a custom timeout is passed" {
            val customTimeoutCheck = EmptyCallStatusCheck(logger, Duration.ofMinutes(10), clock)
            "the check" {
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