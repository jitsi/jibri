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

package org.jitsi.jibri.selenium.status_checks

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Track the most recent timestamp at which we transitioned from
 * an event having not occurred to when it did occur.  Note this
 * tracks the timestamp of that *transition*, not the most recent
 * time the event itself occurred.
 */
class StateTransitionTimeTracker(private val clock: Clock) {
    var timestampTransitionOccured: Instant? = null
        private set

    fun maybeUpdate(eventOccurred: Boolean) {
        if (eventOccurred && timestampTransitionOccured == null) {
            timestampTransitionOccured = clock.instant()
        } else if (!eventOccurred) {
            timestampTransitionOccured = null
        }
    }

    fun exceededTimeout(timeout: Duration): Boolean {
        return timestampTransitionOccured?.let {
            Duration.between(it, clock.instant()) > timeout
        } ?: false
    }
}
