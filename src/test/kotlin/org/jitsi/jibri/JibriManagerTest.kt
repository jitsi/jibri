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
package org.jitsi.jibri

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.StreamingParams

class JibriManagerTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val jibriManager = JibriManager()
    val publishedStatuses = mutableListOf<Any>()
    jibriManager.addStatusHandler { publishedStatuses.add(it) }

    fun streamingParams(streamId: String) = StreamingParams(
        callParams = CallParams(CallUrlInfo("http://example.com", "room")),
        sessionId = "session_id",
        callLoginParams = XmppCredentials(domain = "domain", username = "username", password = "password"),
        rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/$streamId"
    )

    context("Starting a stream with an invalid RTMP URL") {
        should("be rejected without making the instance busy") {
            shouldThrow<BadRequestException> {
                jibriManager.startStreaming(ServiceParams(usageTimeoutMinutes = 0), streamingParams("oooo"))
            }

            jibriManager.busy() shouldBe false
            // The instance must stay idle: publishing BUSY is what makes jicofo drop it from the brewery, and in
            // single-use mode it also causes a restart.
            publishedStatuses.shouldBeEmpty()
        }
    }
})
