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

package org.jitsi.jibri.selenium

import com.tinder.StateMachine
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.NotifyingStateMachine

sealed class SeleniumEvent {
    object CallJoined : SeleniumEvent()
    object FailedToJoinCall : SeleniumEvent()
    object CallEmpty : SeleniumEvent()
    object NoMediaReceived : SeleniumEvent()
    object ChromeHung : SeleniumEvent()
}

sealed class SideEffect

class SeleniumStateMachine : NotifyingStateMachine() {
    private val stateMachine = StateMachine.create<ComponentState, SeleniumEvent, SideEffect> {
        initialState(ComponentState.StartingUp)

        state<ComponentState.StartingUp> {
            on<SeleniumEvent.CallJoined> {
                transitionTo(ComponentState.Running)
            }
            on<SeleniumEvent.FailedToJoinCall> {
                transitionTo(ComponentState.Error(FailedToJoinCall))
            }
            on<SeleniumEvent.ChromeHung> {
                transitionTo(ComponentState.Error(ChromeHung))
            }
        }

        state<ComponentState.Running> {
            on<SeleniumEvent.CallEmpty> {
                transitionTo(ComponentState.Finished)
            }
            on<SeleniumEvent.NoMediaReceived> {
                transitionTo(ComponentState.Error(NoMediaReceived))
            }
            on<SeleniumEvent.ChromeHung> {
                transitionTo(ComponentState.Error(ChromeHung))
            }
        }

        state<ComponentState.Error> {
            on(any<SeleniumEvent>()) {
                dontTransition()
            }
        }

        state<ComponentState.Finished> {
            on(any<SeleniumEvent>()) {
                dontTransition()
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: run {
                throw Exception("Invalid state transition: $it")
            }
            if (validTransition.fromState::class != validTransition.toState::class) {
                notify(validTransition.fromState, validTransition.toState)
            }
        }
    }

    fun transition(event: SeleniumEvent): StateMachine.Transition<*, *, *> = stateMachine.transition(event)
}
