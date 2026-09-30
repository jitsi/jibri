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
package org.jitsi.jibri.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.helpers.SeleniumMockHelper
import org.jitsi.jibri.metrics.JibriMetrics
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.RecordingProfile
import org.jitsi.jibri.util.Resolution

internal class StreamingJibriServiceTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val selenium = SeleniumMockHelper()
    private val capturer: FfmpegCapturer = mockk(relaxed = true)
    private val jibriMetrics: JibriMetrics = mockk(relaxed = true)

    private fun startService(recordingProfile: RecordingProfile?) = StreamingJibriService(
        StreamingParams(
            CallParams(CallUrlInfo("baseUrl", "callName")),
            "session_id",
            XmppCredentials("domain", 8080, "username", "password"),
            rtmpUrl = "rtmp://example.com/live/key",
            recordingProfile = recordingProfile
        ),
        selenium.mock,
        capturer,
        jibriMetrics
    ).start()

    private fun joinedUrlParams(): List<String> {
        val callUrlInfo = slot<CallUrlInfo>()
        verify { selenium.mock.joinCall(capture(callUrlInfo), any(), any(), any()) }
        return callUrlInfo.captured.urlParams
    }

    init {
        context("starting a stream with a tile layout") {
            startService(
                RecordingProfile(
                    canvas = Resolution(2582, 748),
                    tileResolution = Resolution(1280, 720),
                    tileCount = 2,
                    maxFullResolutionParticipants = 3
                )
            )

            should("pass the client options of the layout") {
                joinedUrlParams() shouldContain "config.disableTileEnlargement=true"
                joinedUrlParams() shouldContain "config.maxFullResolutionParticipants=3"
            }
            context("and selenium joins the call") {
                selenium.startSuccessfully()

                should("put the client in tile view") {
                    verify { selenium.mock.setTileView(true) }
                }
                should("start the capturer") {
                    verify { capturer.start(any()) }
                }
            }
        }
        context("starting a stream without a tile layout") {
            startService(null)

            should("not change the client options") {
                joinedUrlParams() shouldNotContain "config.disableTileEnlargement=true"
            }
            context("and selenium joins the call") {
                selenium.startSuccessfully()

                should("not put the client in tile view") {
                    verify(exactly = 0) { selenium.mock.setTileView(any()) }
                }
            }
        }
    }
}
