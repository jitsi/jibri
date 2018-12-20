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
import org.jitsi.jibri.capture.ffmpeg.executor.ErrorScope
import org.jitsi.jibri.status.ComponentState

sealed class SeleniumEvent {
    object CallJoined : SeleniumEvent()
    object CallEmpty : SeleniumEvent()
    object NoMediaReceived : SeleniumEvent()
    object ChromeHung : SeleniumEvent()
}

sealed class SideEffect {

}


//TODO: write an abstract state machine which handles the state transition handlers, etc.  can also have all events
// inherit from a single event class so that we can define the 'transition' method in the base class too?
class SeleniumStateMachine {
    private val stateMachine = StateMachine.create<ComponentState, SeleniumEvent, SideEffect> {
        initialState(ComponentState.StartingUp)

        state<ComponentState.StartingUp> {
            on<SeleniumEvent.CallJoined> {
                transitionTo(ComponentState.Running)
            }
        }

        state<ComponentState.Running> {
            on<SeleniumEvent.CallEmpty> {
                transitionTo(ComponentState.Finished)
            }
            on<SeleniumEvent.NoMediaReceived> {
                transitionTo(ComponentState.Error(ErrorScope.SESSION, "No media received"))
            }
            on<SeleniumEvent.ChromeHung> {
                transitionTo(ComponentState.Error(ErrorScope.SESSION, "Chrome hung"))
            }
        }

        state<ComponentState.Error> {}

        state<ComponentState.Finished> {}

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            stateTranstionHandlers.forEach { handler ->
                handler(validTransition.fromState, validTransition.toState)
            }
        }
    }

    private val stateTranstionHandlers = mutableListOf<(ComponentState, ComponentState) -> Unit>()

    fun onStateTransition(handler: (ComponentState, ComponentState) -> Unit) {
        stateTranstionHandlers.add(handler)
    }

    fun transition(event: SeleniumEvent): StateMachine.Transition<*, *, *> = stateMachine.transition(event)
}