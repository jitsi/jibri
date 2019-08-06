/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
 *
 */

package org.jitsi.jibri.capture.ffmpeg

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.matchers.collections.contain
import io.kotlintest.matchers.collections.shouldNotBeEmpty
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.OsDetector
import org.jitsi.jibri.util.OsType
import org.jitsi.jibri.util.ProcessExited
import org.jitsi.jibri.util.ProcessFailedToStart
import org.jitsi.jibri.util.ProcessRunning
import org.jitsi.jibri.util.ProcessState

internal class FfmpegCapturerTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true

    private val osDetector: OsDetector = mock()
    private val ffmpeg: JibriSubprocess = mock()
    private val ffmpegStateHandler = argumentCaptor<(ProcessState) -> Unit>()
    private val capturerStateUpdates = mutableListOf<ComponentState>()
    private val sink: Sink = mock()

    private val FFMPEG_ENCODING_STATE = ProcessState(ProcessRunning(), "frame=42")
    private val FFMPEG_ERROR_STATE = ProcessState(ProcessExited(255), "rtmp://blah Input/output error")
    private val FFMPEG_FAILED_TO_START = ProcessState(ProcessFailedToStart(), "Failed to start")

    override fun beforeSpec(description: Description, spec: Spec) {
        super.beforeSpec(description, spec)

        whenever(sink.format).thenReturn("format")
        whenever(sink.options).thenReturn(arrayOf("option1", "option2"))
        whenever(sink.path).thenReturn("path")

        whenever(ffmpeg.addStatusHandler(ffmpegStateHandler.capture())).thenAnswer { }
    }

    private fun createCapturer(): FfmpegCapturer {
        val capturer = FfmpegCapturer(osDetector, ffmpeg)
        capturer.addStatusHandler { status ->
            capturerStateUpdates.add(status)
        }
        return capturer
    }

    init {
        "on any supported platform" {
            // We arbitrarily use linux
            whenever(osDetector.getOsType()).thenReturn(OsType.LINUX)
            val ffmpegCapturer = createCapturer()
            "when launching ffmpeg succeeds" {
                whenever(ffmpeg.launch(any(), any())).thenAnswer {
                    ffmpegStateHandler.firstValue(FFMPEG_ENCODING_STATE)
                }
                ffmpegCapturer.start(sink)
                "capturer" {
                    should("report its status as running") {
                        capturerStateUpdates.shouldNotBeEmpty()
                        capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Running>()
                    }
                }
                "and then encounters an error" {
                    ffmpegStateHandler.firstValue(FFMPEG_ERROR_STATE)
                    "capturer" {
                        should("report its status as error") {
                            capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Error>()
                        }
                    }
                }
            }
            "when ffmpeg fails to start" {
                whenever(ffmpeg.launch(any(), any())).thenAnswer {
                    ffmpegStateHandler.firstValue(FFMPEG_FAILED_TO_START)
                }
                ffmpegCapturer.start(sink)
                "capturer" {
                    should("report its status as a system error") {
                        capturerStateUpdates.shouldNotBeEmpty()
                        val status = capturerStateUpdates.last()
                        status.shouldBeInstanceOf<ComponentState.Error>()
                        status as ComponentState.Error
                        status.errorScope shouldBe ErrorScope.SYSTEM
                    }
                }
            }
            "stop" {
                should("call stop on the executor") {
                    ffmpegCapturer.stop()
                    verify(ffmpeg).stop()
                }
            }
        }
        "on linux" {
            whenever(osDetector.getOsType()).thenReturn(OsType.LINUX)
            val ffmpegCapturer = createCapturer()
            "the command" {
                should("be correct for linux") {
                    ffmpegCapturer.start(sink)
                    val commandCaptor = argumentCaptor<List<String>>()
                    verify(ffmpeg).launch(commandCaptor.capture(), any())
                    commandCaptor.firstValue should contain("x11grab")
                    commandCaptor.firstValue should contain("alsa")
                    commandCaptor.firstValue should contain("option1")
                    commandCaptor.firstValue should contain("option2")
                }
            }
        }
        "on mac" {
            whenever(osDetector.getOsType()).thenReturn(OsType.MAC)
            val ffmpegCapturer = createCapturer()
            "the command" {
                should("be correct for mac") {
                    ffmpegCapturer.start(sink)
                    val commandCaptor = argumentCaptor<List<String>>()
                    verify(ffmpeg).launch(commandCaptor.capture(), any())
                    commandCaptor.firstValue should contain("avfoundation")
                }
            }
        }
        "on an unsupported platform" {
            whenever(osDetector.getOsType()).thenReturn(OsType.UNSUPPORTED)
            shouldThrow<UnsupportedOsException> {
                FfmpegCapturer(osDetector, ffmpeg)
            }
        }
    }
}
