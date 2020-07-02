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

package org.jitsi.jibri.service

import com.tinder.StateMachine
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.NotifyingStateMachine

sealed class JibriServiceEvent {
    class SubComponentStartingUp(val componentId: String, val subState: ComponentState.StartingUp) : JibriServiceEvent()
    class SubComponentRunning(val componentId: String, val subState: ComponentState.Running) : JibriServiceEvent()
    class SubComponentError(val componentId: String, val subState: ComponentState.Error) : JibriServiceEvent()
    class SubComponentFinished(val componentId: String, val subState: ComponentState.Finished) : JibriServiceEvent()
}

fun ComponentState.toJibriServiceEvent(componentId: String): JibriServiceEvent {
    return when (this) {
        is ComponentState.StartingUp -> JibriServiceEvent.SubComponentStartingUp(componentId, this)
        is ComponentState.Running -> JibriServiceEvent.SubComponentRunning(componentId, this)
        is ComponentState.Error -> JibriServiceEvent.SubComponentError(componentId, this)
        is ComponentState.Finished -> JibriServiceEvent.SubComponentFinished(componentId, this)
    }
}

sealed class JibriServiceSideEffect

/**
 * A state machine for the services to use to handle status updates from their subcomponents.  Subcomponents should
 * be registered via [JibriServiceStateMachine.registerSubComponent] such that the [JibriService]'s overall state
 * can be computed as a function of the subcomponents' states.
 */
class JibriServiceStateMachine : NotifyingStateMachine() {
    private val stateMachine = StateMachine.create<ComponentState, JibriServiceEvent, JibriServiceSideEffect> {
        initialState(ComponentState.StartingUp)

        state<ComponentState.StartingUp> {
            on<JibriServiceEvent.SubComponentError> {
                subComponentStates[it.componentId] = it.subState
                transitionTo(ComponentState.Error(it.subState.error))
            }
            on<JibriServiceEvent.SubComponentFinished> {
                subComponentStates[it.componentId] = it.subState
                transitionTo(ComponentState.Finished)
            }
            on<JibriServiceEvent.SubComponentRunning> {
                subComponentStates[it.componentId] = it.subState
                if (subComponentStates.values.all { it is ComponentState.Running }) {
                    transitionTo(ComponentState.Running)
                } else {
                    dontTransition()
                }
            }
            on<JibriServiceEvent.SubComponentStartingUp> {
                dontTransition()
            }
        }

        state<ComponentState.Running> {
            on<JibriServiceEvent.SubComponentError> {
                subComponentStates[it.componentId] = it.subState
                transitionTo(ComponentState.Error(it.subState.error))
            }
            on<JibriServiceEvent.SubComponentFinished> {
                subComponentStates[it.componentId] = it.subState
                transitionTo(ComponentState.Finished)
            }
        }

        state<ComponentState.Error> {
            on(any<JibriServiceEvent>()) {
                dontTransition()
            }
        }

        state<ComponentState.Finished> {
            on(any<JibriServiceEvent>()) {
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

    private val subComponentStates = mutableMapOf<String, ComponentState>()

    fun registerSubComponent(componentKey: String) {
        // TODO: we'll assume everything starts in 'starting up' ?
        subComponentStates[componentKey] = ComponentState.StartingUp
    }

    fun transition(event: JibriServiceEvent): StateMachine.Transition<*, *, *> = stateMachine.transition(event)
}
