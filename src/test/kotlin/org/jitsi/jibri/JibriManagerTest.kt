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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.RequestedRecordingParams
import org.jitsi.jibri.service.Screen
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.util.Resolution
import org.jitsi.jibri.util.Xrandr
import org.jitsi.jibri.util.XrandrException

class JibriManagerTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val jibriManager = JibriManager()
    val publishedStatuses = mutableListOf<Any>()
    jibriManager.addStatusHandler { publishedStatuses.add(it) }

    val callParams = CallParams(CallUrlInfo("http://example.com", "room"))
    val login = XmppCredentials(domain = "domain", username = "username", password = "password")

    fun streamingParams(streamId: String, recordingParams: RequestedRecordingParams? = null) = StreamingParams(
        callParams = callParams,
        sessionId = "session_id",
        callLoginParams = login,
        rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/$streamId",
        recordingParams = recordingParams
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

    context("With xrandr enabled") {
        val defaultResolution = Resolution(1920, 1080)
        val xrandr: Xrandr = mockk(relaxed = true)
        // Stop the session before it creates a service, which would start Chrome.
        every { xrandr.setResolution(any()) } throws XrandrException("no such mode")
        val manager = JibriManager(
            Screen(mockk(relaxed = true), xrandr, xrandrEnabled = true, defaultResolution = defaultResolution)
        )
        val statuses = mutableListOf<Any>()
        manager.addStatusHandler { statuses.add(it) }
        val serviceParams = ServiceParams(usageTimeoutMinutes = 0)

        context("a file recording without recording parameters") {
            shouldThrow<XrandrException> {
                manager.startFileRecording(serviceParams, FileRecordingRequestParams(callParams, "session_id", login))
            }

            should("set the default resolution, because an earlier session can have left another one") {
                verify { xrandr.setResolution(defaultResolution) }
            }
            should("report that the instance is unhealthy when that fails, and stay idle") {
                statuses shouldContainExactly listOf(ComponentHealthStatus.UNHEALTHY)
                manager.busy() shouldBe false
            }
        }
        context("a stream with a tile layout") {
            shouldThrow<XrandrException> {
                manager.startStreaming(
                    serviceParams,
                    streamingParams("abcd-1234-efgh-5678-ijkl", RequestedRecordingParams("1280x720", "2"))
                )
            }

            should("set the canvas of the layout") {
                verify { xrandr.setResolution(Resolution(2582, 748)) }
            }
        }
        context("a stream without recording parameters") {
            shouldThrow<XrandrException> {
                manager.startStreaming(serviceParams, streamingParams("abcd-1234-efgh-5678-ijkl"))
            }

            should("set the default resolution") {
                verify { xrandr.setResolution(defaultResolution) }
            }
        }
    }

    context("With xrandr disabled") {
        val xrandr: Xrandr = mockk(relaxed = true)
        val manager = JibriManager(
            Screen(mockk(relaxed = true), xrandr, xrandrEnabled = false, defaultResolution = Resolution(1920, 1080))
        )

        should("refuse a tile layout without touching the screen") {
            shouldThrow<BadRequestException> {
                manager.startFileRecording(
                    ServiceParams(usageTimeoutMinutes = 0),
                    FileRecordingRequestParams(
                        callParams,
                        "session_id",
                        login,
                        RequestedRecordingParams("1280x720", "2")
                    )
                )
            }
            verify(exactly = 0) { xrandr.setResolution(any()) }
            manager.busy() shouldBe false
        }
    }
})
