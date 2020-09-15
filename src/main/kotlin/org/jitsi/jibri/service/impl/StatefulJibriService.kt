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

package org.jitsi.jibri.service.impl

import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStateMachine
import org.jitsi.jibri.service.toJibriServiceEvent
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.StatusPublisher
import java.util.logging.Logger

abstract class StatefulJibriService(private val name: String) : JibriService() {
    private val stateMachine = JibriServiceStateMachine()

    /**
     * The [Logger] for this class
     */
    protected val logger = Logger.getLogger(this::class.qualifiedName)

    init {
        stateMachine.onStateTransition { oldState, newState -> this.onServiceStateChange(oldState, newState) }
    }

    private fun onServiceStateChange(oldState: ComponentState, newState: ComponentState) {
        logger.info("$name service transitioning from state $oldState to $newState")
        publishStatus(newState)
    }

    protected fun registerSubComponent(subComponentId: String, subComponent: StatusPublisher<ComponentState>) {
        stateMachine.registerSubComponent(subComponentId)
        subComponent.addStatusHandler { stateUpdate ->
            stateMachine.transition(stateUpdate.toJibriServiceEvent(subComponentId))
        }
    }
}
