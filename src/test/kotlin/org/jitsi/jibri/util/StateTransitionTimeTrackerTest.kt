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

package org.jitsi.jibri.util

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jitsi.test.time.FakeClock
import org.jitsi.utils.ms
import org.jitsi.utils.secs

class StateTransitionTimeTrackerTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val clock = FakeClock()
    val sttt = StateTransitionTimeTracker(clock)

    context("StateTransitionTimeTracker") {
        context("when the event occurs") {
            sttt.maybeUpdate(true)
            should("correctly record the time at which it occurred") {
                clock.elapse(5.secs)
                sttt.exceededTimeout(2.secs) shouldBe true
                sttt.exceededTimeout(10.secs) shouldBe false
            }
            context("and then goes away") {
                sttt.maybeUpdate(false)
                clock.elapse(5.secs)
                should("never report that the timeout has been exceeded") {
                    sttt.exceededTimeout(1.ms) shouldBe false
                    sttt.exceededTimeout(Long.MAX_VALUE.ms) shouldBe false
                }
            }
            context("and then happens again after some time") {
                clock.elapse(5.secs)
                sttt.maybeUpdate(true)
                clock.elapse(3.secs)
                should("not update the last-occurred timestamp") {
                    sttt.exceededTimeout(5.secs) shouldBe true
                }
            }
        }
    }
})
