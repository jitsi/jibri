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

import io.kotlintest.IsolationMode
import io.kotlintest.Spec
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.status.ComponentState

internal class SeleniumStateMachineTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val stateUpdates = mutableListOf<Pair<ComponentState, ComponentState>>()
    private val seleniumStateMachine = SeleniumStateMachine()

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        seleniumStateMachine.onStateTransition { fromState, toState ->
            stateUpdates.add((fromState to toState))
        }
    }

    init {
        "When starting up" {
            "and the call is joined" {
                seleniumStateMachine.transition(SeleniumEvent.CallJoined)
                should("transition to running") {
                    stateUpdates should haveSize(1)
                    stateUpdates.first() shouldBe (ComponentState.StartingUp to ComponentState.Running)
                }
            }
            "and an error occurs" {
                seleniumStateMachine.transition(SeleniumEvent.FailedToJoinCall)
                should("transition to error") {
                    stateUpdates should haveSize(1)
                    stateUpdates.first().second should beInstanceOf<ComponentState.Error>()
                }
                "and then another event occurs" {
                    seleniumStateMachine.transition(SeleniumEvent.CallEmpty)
                    should("not fire another update") {
                        stateUpdates should haveSize(1)
                    }
                }
            }
            "and an invalid event occurs" {
                should("throw an exception") {
                    shouldThrow<Exception> {
                        seleniumStateMachine.transition(SeleniumEvent.NoMediaReceived)
                    }
                }
            }
        }
    }
}