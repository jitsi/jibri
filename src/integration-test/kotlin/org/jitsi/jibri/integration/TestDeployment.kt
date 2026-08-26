/*
 * Copyright @ 2026 - present 8x8, Inc.
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

package org.jitsi.jibri.integration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The docker-jitsi-meet deployment the integration tests run against.  See
 * resources/integration-tests/deployment.sh for how to bring one up.
 */
object TestDeployment {
    /** The url of the deployment, without a trailing slash. */
    val baseUrl: String = System.getProperty("jibri.test.base-url", "https://localhost:8443").trimEnd('/')

    /** Whether the browsers are started headless.  Set to false to watch the tests run. */
    val headless: Boolean = System.getProperty("jibri.test.headless", "true").toBoolean()

    /** A room name which is unique per test run, so tests never land in a room left over from a previous one. */
    fun randomRoomName(prefix: String): String = "$prefix${System.nanoTime().toString().takeLast(9)}"
}

/**
 * Retries [block] until it returns without throwing, or until [timeout] has passed, in which case the last
 * failure is rethrown.  Conferences settle asynchronously (participants join, ICE connects, mute state
 * propagates over XMPP), so almost nothing about them can be asserted on immediately.
 */
fun <T> await(
    timeout: Duration = 30.seconds,
    interval: Duration = 500.milliseconds,
    description: String = "condition",
    block: () -> T
): T {
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    var lastFailure: Throwable
    while (true) {
        try {
            return block()
        } catch (t: Throwable) {
            lastFailure = t
        }
        if (System.nanoTime() >= deadline) {
            throw AssertionError("Timed out after $timeout waiting for $description", lastFailure)
        }
        Thread.sleep(interval.inWholeMilliseconds)
    }
}
