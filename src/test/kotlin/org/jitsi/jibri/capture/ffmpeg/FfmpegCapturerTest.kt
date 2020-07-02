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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val osDetector: OsDetector = mockk()
    private val ffmpeg: JibriSubprocess = mockk(relaxed = true)
    private val ffmpegStateHandler = slot<(ProcessState) -> Unit>()
    private val capturerStateUpdates = mutableListOf<ComponentState>()
    private val sink: Sink = mockk()

    private val FFMPEG_ENCODING_STATE = ProcessState(ProcessRunning(), "frame=42")
    private val FFMPEG_ERROR_STATE = ProcessState(ProcessExited(255), "rtmp://blah Input/output error")
    private val FFMPEG_FAILED_TO_START = ProcessState(ProcessFailedToStart(), "Failed to start")

    private fun createCapturer(): FfmpegCapturer {
        val capturer = FfmpegCapturer(osDetector, ffmpeg)
        capturer.addStatusHandler { status ->
            capturerStateUpdates.add(status)
        }
        return capturer
    }

    init {
        beforeSpec {
            every { sink.format } returns "format"
            every { sink.options } returns arrayOf("option1", "option2")
            every { sink.path } returns "path"

            every { ffmpeg.addStatusHandler(capture(ffmpegStateHandler)) } just Runs
        }
        context("on any supported platform") {
            // We arbitrarily use linux
            every { osDetector.getOsType() } returns OsType.LINUX
            val ffmpegCapturer = createCapturer()
            context("when launching ffmpeg succeeds") {
                every { ffmpeg.launch(any(), any()) } answers {
                    ffmpegStateHandler.captured(FFMPEG_ENCODING_STATE)
                }
                ffmpegCapturer.start(sink)
                context("capturer") {
                    should("report its status as running") {
                        capturerStateUpdates.shouldNotBeEmpty()
                        capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Running>()
                    }
                }
                context("and then finishes cleanly") {
                    ffmpegStateHandler.captured(ProcessState(ProcessRunning(), "Exiting with signal 2"))
                    context("capturer") {
                        should("report its status as finished") {
                            capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Finished>()
                        }
                    }
                }
                context("and then encounters an error") {
                    ffmpegStateHandler.captured(FFMPEG_ERROR_STATE)
                    context("capturer") {
                        should("report its status as error") {
                            capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Error>()
                        }
                    }
                }
                context("and quits abruptly") {
                    context("with a normal last output line") {
                        ffmpegStateHandler.captured(ProcessState(ProcessExited(139), "frame=42"))
                        context("capture") {
                            should("report its stats as error") {
                                val error = capturerStateUpdates.last()
                                error.shouldBeInstanceOf<ComponentState.Error>()
                                error as ComponentState.Error
                                error.error.scope shouldBe ErrorScope.SESSION
                            }
                        }
                    }
                    context("with an unknown output line") {
                        ffmpegStateHandler.captured(ProcessState(ProcessExited(139), "something!"))
                        context("capture") {
                            should("report its stats as error") {
                                val error = capturerStateUpdates.last()
                                error.shouldBeInstanceOf<ComponentState.Error>()
                                error as ComponentState.Error
                                error.error.scope shouldBe ErrorScope.SESSION
                            }
                        }
                    }
                }
            }
            context("when ffmpeg fails to start") {
                every { ffmpeg.launch(any(), any()) } answers {
                    ffmpegStateHandler.captured(FFMPEG_FAILED_TO_START)
                }
                ffmpegCapturer.start(sink)
                context("capturer") {
                    should("report its status as a system error") {
                        capturerStateUpdates.shouldNotBeEmpty()
                        val status = capturerStateUpdates.last()
                        status.shouldBeInstanceOf<ComponentState.Error>()
                        status as ComponentState.Error
                        status.error.scope shouldBe ErrorScope.SYSTEM
                    }
                }
            }
            context("stop") {
                should("call stop on the executor") {
                    ffmpegCapturer.stop()
                    verify { ffmpeg.stop() }
                }
            }
        }
        context("on linux") {
            every { osDetector.getOsType() } returns OsType.LINUX
            val ffmpegCapturer = createCapturer()
            context("the command") {
                should("be correct for linux") {
                    ffmpegCapturer.start(sink)
                    val commandCaptor = slot<List<String>>()
                    verify { ffmpeg.launch(capture(commandCaptor), any()) }
                    commandCaptor.captured should contain("x11grab")
                    commandCaptor.captured should contain("alsa")
                    commandCaptor.captured should contain("option1")
                    commandCaptor.captured should contain("option2")
                }
            }
        }
        context("on mac") {
            every { osDetector.getOsType() } returns OsType.MAC
            val ffmpegCapturer = createCapturer()
            context("the command") {
                should("be correct for mac") {
                    ffmpegCapturer.start(sink)
                    val commandCaptor = slot<List<String>>()
                    verify { ffmpeg.launch(capture(commandCaptor), any()) }
                    commandCaptor.captured should contain("avfoundation")
                }
            }
        }
        context("on an unsupported platform") {
            every { osDetector.getOsType() } returns OsType.UNSUPPORTED
            shouldThrow<UnsupportedOsException> {
                FfmpegCapturer(osDetector, ffmpeg)
            }
        }
    }
}
