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

package org.jitsi.jibri.selenium.statuschecks

import org.jitsi.jibri.EmptyCallException
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.util.StateTransitionTimeTracker
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import java.time.Clock
import java.time.Duration

class EmptyCallCheck(
    parentLogger: Logger,
    private val callEmptyTimeout: Duration = defaultCallEmptyTimeout,
    private val clock: Clock = Clock.systemUTC()
) : CallCheck {
    private val logger = createChildLogger(parentLogger)
    // The timestamp at which we last saw the call transition from
    // non-empty to empty
    private val callWentEmptyTime = StateTransitionTimeTracker(clock)

    override fun runCheck(callPage: CallPage) {
        val now = clock.instant()
        callWentEmptyTime.maybeUpdate(callPage.isCallEmpty())

        if (callWentEmptyTime.exceededTimeout(callEmptyTimeout)) {
            logger.info(
                "Call has been empty since ${callWentEmptyTime.timestampTransitionOccurred} " +
                    "(${Duration.between(callWentEmptyTime.timestampTransitionOccurred, now)} ago). " +
                    "Throwing EmptyCallException."
            )
            throw EmptyCallException
        }
    }

    companion object {
        val defaultCallEmptyTimeout: Duration by config {
            "jibri.call-status-checks.default-call-empty-timeout".from(Config.configSource)
        }
    }
}

// <= 1 since the count will include jibri itself
private fun CallPage.isCallEmpty() = getNumParticipants() <= 1
