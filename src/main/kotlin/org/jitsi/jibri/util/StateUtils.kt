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

import org.jitsi.jibri.status.ComponentState

/**
 * These helpers make it easy to do work based on a component transitioning to a particular state.
 * For example, if we have subcomponents Foo and Bar, and Bar shouldn't start until Foo has reached
 * [ComponentState.Running], you can do:
 *
 * whenever(bar).transitionsTo(ComponentState.Running) {
 *     foo.start()
 * }
 */
class ComponentStateTransitioner(private val statusPublisher: StatusPublisher<ComponentState>) {
    init {
    }

    fun transitionsTo(desiredState: ComponentState, block: () -> Unit) {
        statusPublisher.addTemporaryHandler { state ->
            if (state == desiredState) {
                block()
                return@addTemporaryHandler false
            }
            true
        }
    }

    /**
     * We need a special method for the error state, since the error state is stateful and we don't use a single
     * instance to model the state.
     */
    fun transitionsToError(block: () -> Unit) {
        statusPublisher.addTemporaryHandler { state ->
            if (state is ComponentState.Error) {
                block()
                return@addTemporaryHandler false
            }
            true
        }
    }
}

fun whenever(statusPublisher: StatusPublisher<ComponentState>): ComponentStateTransitioner =
        ComponentStateTransitioner(statusPublisher)
