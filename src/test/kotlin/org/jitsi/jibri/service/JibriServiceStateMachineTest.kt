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

package org.jitsi.jibri.service

import com.tinder.StateMachine
import io.kotlintest.specs.ShouldSpec
import com.tinder.StateMachine.Matcher.Companion.any

sealed class State {
    object Off : State()
    object On : State()
}

sealed class Event {
    class Event1 : Event()
    class Event2 : Event()
    class Event3 : Event()
}

sealed class SideEffect

internal class StateMachineTest : ShouldSpec() {
    private fun event1or3() = any<Event, Event>().where {
        (this is Event.Event1) || (this is Event.Event3)
    }
    val stateMachine = StateMachine.create<State, Event, SideEffect> {
        initialState(State.Off)

        state<State.Off> {
            on<Event.Event1> {
                println("event 1")
                transitionTo(State.On)
            }
        }

        state<State.On> {
            on<Event.Event1> {
                println("event 1")
                transitionTo(State.On)
            }
            on(event1or3()) {
                dontTransition()
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: run {
                println("invalid transition!")
                return@onTransition
            }
            println("valid transition: $validTransition")
        }
    }

    init {
        stateMachine.transition(Event.Event1())

        stateMachine.transition(Event.Event3())
    }
}