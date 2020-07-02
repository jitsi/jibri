/*
 * Copyright @ 2019 - present 8x8, Inc.
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

package org.jitsi.jibri.selenium

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.beInstanceOf
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.status.ComponentState

internal class SeleniumStateMachineTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val stateUpdates = mutableListOf<Pair<ComponentState, ComponentState>>()
    private val seleniumStateMachine = SeleniumStateMachine()

    init {
        beforeSpec {
            seleniumStateMachine.onStateTransition { fromState, toState ->
                stateUpdates.add((fromState to toState))
            }
        }

        context("When starting up") {
            context("and the call is joined") {
                seleniumStateMachine.transition(SeleniumEvent.CallJoined)
                should("transition to running") {
                    stateUpdates should haveSize(1)
                    stateUpdates.first() shouldBe (ComponentState.StartingUp to ComponentState.Running)
                }
            }
            context("and an error occurs") {
                seleniumStateMachine.transition(SeleniumEvent.FailedToJoinCall)
                should("transition to error") {
                    stateUpdates should haveSize(1)
                    stateUpdates.first().second should beInstanceOf<ComponentState.Error>()
                }
                context("and then another event occurs") {
                    seleniumStateMachine.transition(SeleniumEvent.CallEmpty)
                    should("not fire another update") {
                        stateUpdates should haveSize(1)
                    }
                }
            }
            context("and an invalid event occurs") {
                should("throw an exception") {
                    shouldThrow<Exception> {
                        seleniumStateMachine.transition(SeleniumEvent.NoMediaReceived)
                    }
                }
            }
        }
    }
}
