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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.matchers.collections.contain
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.OsDetector
import org.jitsi.jibri.util.OsType

internal class FfmpegCapturerTest : ShouldSpec() {
    private val osDetector: OsDetector = mock()
    private val ffmpegExecutor: FfmpegExecutor = mock()
    private val sink: Sink = mock()

    override fun isInstancePerTest(): Boolean = true

    init {
        whenever(sink.format).thenReturn("format")
        whenever(sink.options).thenReturn(arrayOf("option1", "option2"))
        whenever(sink.path).thenReturn("path")

        "on linux" {
            whenever(osDetector.getOsType()).thenReturn(OsType.LINUX)
            val ffmpegCapturer = FfmpegCapturer(osDetector, ffmpegExecutor)
            "the command" {
                val commandCaptor = argumentCaptor<List<String>>()
                whenever(ffmpegExecutor.launchFfmpeg(commandCaptor.capture())).thenReturn(true)
                should("be correct for linux") {
                    ffmpegCapturer.start(sink)
                    commandCaptor.firstValue should contain("x11grab")
                    commandCaptor.firstValue should contain("alsa")
                    commandCaptor.firstValue should contain("option1")
                    commandCaptor.firstValue should contain("option2")
                }
            }
            "when launching succeeds" {
                whenever(ffmpegExecutor.launchFfmpeg(any())).thenReturn(true)
                "and ffmpeg is healthy" {
                    whenever(ffmpegExecutor.isHealthy()).thenReturn(true)
                    "start" {
                        should("return true") {
                            ffmpegCapturer.start(sink) shouldBe true
                        }
                    }
                }
                "and ffmpeg is healthy after a bit" {
                    var numCalls = 0
                    whenever(ffmpegExecutor.isHealthy()).thenAnswer {
                        numCalls++ > 5
                    }
                    whenever(ffmpegExecutor.getExitCode()).thenReturn(null)
                    "start" {
                        should("return true") {
                            ffmpegCapturer.start(sink) shouldBe true
                        }
                    }
                }
                "and ffmpeg isn't healthy" {
                    whenever(ffmpegExecutor.isHealthy()).thenReturn(false)
                    whenever(ffmpegExecutor.getExitCode()).thenReturn(null)
                        "start" {
                            should("return false") {
                                ffmpegCapturer.start(sink) shouldBe false
                        }
                    }
                }
                "and ffmpeg exits" {
                    whenever(ffmpegExecutor.isHealthy()).thenReturn(false)
                    whenever(ffmpegExecutor.getExitCode()).thenReturn(42)
                    "start" {
                        should("return false") {
                            ffmpegCapturer.start(sink) shouldBe false
                        }
                    }
                }
            }
            "when launching fails" {
                whenever(ffmpegExecutor.launchFfmpeg(any())).thenReturn(false)
                ffmpegCapturer.start(sink) shouldBe false
            }
            "stop" {
                should("call stop on the executor") {
                    ffmpegCapturer.stop()
                    verify(ffmpegExecutor).stopFfmpeg()
                }
            }
        }
        "on mac" {
            whenever(osDetector.getOsType()).thenReturn(OsType.MAC)
            val ffmpegCapturer = FfmpegCapturer(osDetector, ffmpegExecutor)
            "the command" {
                val commandCaptor = argumentCaptor<List<String>>()
                whenever(ffmpegExecutor.launchFfmpeg(commandCaptor.capture())).thenReturn(true)
                should("be correct for linux") {
                    ffmpegCapturer.start(sink)
                    println(commandCaptor.firstValue)
                    commandCaptor.firstValue should contain("avfoundation")
                }
            }
        }
        "on an unsupported platform" {
            whenever(osDetector.getOsType()).thenReturn(OsType.UNSUPPORTED)
            shouldThrow<UnsupportedOsException> {
                FfmpegCapturer(osDetector, ffmpegExecutor)
            }
        }
    }
}
