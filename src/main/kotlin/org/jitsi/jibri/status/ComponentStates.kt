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

package org.jitsi.jibri.status

import org.jitsi.jibri.error.JibriError

sealed class ComponentState {
    object StartingUp : ComponentState() {
        override fun toString(): String = "Starting up"
    }
    object Running : ComponentState() {
        override fun toString(): String = "Running"
    }
    class Error(val error: JibriError) : ComponentState() {
        override fun toString(): String = error.toString()
    }
    object Finished : ComponentState() {
        override fun toString(): String = "Finished"
    }
}

enum class ErrorScope {
    /**
     * [SESSION] errors are errors which only affect the current session.  A session error still leaves Jibri as a
     * whole 'healthy'
     */
    SESSION,
    /**
     * [SYSTEM] errors are unrecoverable, and will put Jibri in an unhealthy state
     */
    SYSTEM
}
