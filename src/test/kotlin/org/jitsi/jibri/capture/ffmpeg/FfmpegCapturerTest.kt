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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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

    private val osDetector: OsDetector = mock()
    private val ffmpeg: JibriSubprocess = mock()
    private val ffmpegStateHandler = argumentCaptor<(ProcessState) -> Unit>()
    private val capturerStateUpdates = mutableListOf<ComponentState>()
    private val sink: Sink = mock()

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
            whenever(sink.format).thenReturn("format")
            whenever(sink.options).thenReturn(arrayOf("option1", "option2"))
            whenever(sink.path).thenReturn("path")

            whenever(ffmpeg.addStatusHandler(ffmpegStateHandler.capture())).thenAnswer { }
        }
        context("on any supported platform") {
            // We arbitrarily use linux
            whenever(osDetector.getOsType()).thenReturn(OsType.LINUX)
            val ffmpegCapturer = createCapturer()
            context("when launching ffmpeg succeeds") {
                whenever(ffmpeg.launch(any(), any())).thenAnswer {
                    ffmpegStateHandler.firstValue(FFMPEG_ENCODING_STATE)
                }
                ffmpegCapturer.start(sink)
                context("capturer") {
                    should("report its status as running") {
                        capturerStateUpdates.shouldNotBeEmpty()
                        capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Running>()
                    }
                }
                context("and then finishes cleanly") {
                    ffmpegStateHandler.firstValue(ProcessState(ProcessRunning(), "Exiting with signal 2"))
                    context("capturer") {
                        should("report its status as finished") {
                            capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Finished>()
                        }
                    }
                }
                context("and then encounters an error") {
                    ffmpegStateHandler.firstValue(FFMPEG_ERROR_STATE)
                    context("capturer") {
                        should("report its status as error") {
                            capturerStateUpdates.last().shouldBeInstanceOf<ComponentState.Error>()
                        }
                    }
                }
                context("and quits abruptly") {
                    context("with a normal last output line") {
                        ffmpegStateHandler.firstValue(ProcessState(ProcessExited(139), "frame=42"))
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
                        ffmpegStateHandler.firstValue(ProcessState(ProcessExited(139), "something!"))
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
                whenever(ffmpeg.launch(any(), any())).thenAnswer {
                    ffmpegStateHandler.firstValue(FFMPEG_FAILED_TO_START)
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
                    verify(ffmpeg).stop()
                }
            }
        }
        context("on linux") {
            whenever(osDetector.getOsType()).thenReturn(OsType.LINUX)
            val ffmpegCapturer = createCapturer()
            context("the command") {
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
        context("on mac") {
            whenever(osDetector.getOsType()).thenReturn(OsType.MAC)
            val ffmpegCapturer = createCapturer()
            context("the command") {
                should("be correct for mac") {
                    ffmpegCapturer.start(sink)
                    val commandCaptor = argumentCaptor<List<String>>()
                    verify(ffmpeg).launch(commandCaptor.capture(), any())
                    commandCaptor.firstValue should contain("avfoundation")
                }
            }
        }
        context("on an unsupported platform") {
            whenever(osDetector.getOsType()).thenReturn(OsType.UNSUPPORTED)
            shouldThrow<UnsupportedOsException> {
                FfmpegCapturer(osDetector, ffmpeg)
            }
        }
    }
}
