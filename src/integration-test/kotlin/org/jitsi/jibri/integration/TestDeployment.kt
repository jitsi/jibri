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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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

    /**
     * The JWT application the deployment accepts tokens from.  These are fixed rather than generated so
     * that resources/integration-tests/deployment.sh and the tests agree on them without having to pass
     * anything between them; the deployment is local and throwaway.
     */
    const val JWT_APP_ID = "jibri-integration-tests"
    const val JWT_APP_SECRET = "jibri-integration-tests-secret"

    /** The XMPP domain of a stock docker-jitsi-meet deployment. */
    const val XMPP_DOMAIN = "meet.jitsi"

    /**
     * A token identifying a participant as [user].  Participants only carry an identity in their presence
     * when they join with one of these, and that identity is what jibri reports from `getParticipants` and
     * what marks somebody as hidden from the recorder.
     */
    fun jwtFor(user: Map<String, String>): String {
        val now = System.currentTimeMillis() / 1000
        val header = mapOf("alg" to "HS256", "typ" to "JWT")
        val payload = mapOf(
            "iss" to JWT_APP_ID,
            "aud" to JWT_APP_ID,
            "sub" to XMPP_DOMAIN,
            "room" to "*",
            "nbf" to now - 60,
            "exp" to now + 3600,
            "context" to mapOf("user" to user)
        )
        val mapper = jacksonObjectMapper()
        val signingInput = "${base64Url(mapper.writeValueAsBytes(header))}." +
            base64Url(mapper.writeValueAsBytes(payload))
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(JWT_APP_SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }
        return "$signingInput.${base64Url(mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8)))}"
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
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
