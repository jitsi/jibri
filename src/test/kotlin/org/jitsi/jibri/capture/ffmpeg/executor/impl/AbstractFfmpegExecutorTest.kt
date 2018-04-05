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

package org.jitsi.jibri.capture.ffmpeg.executor.impl

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.testHelpers.eventually
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration

class TestableAbstractFfmpegExecutor(fakeProcessBuilder: ProcessBuilder) : AbstractFfmpegExecutor(fakeProcessBuilder) {
    override fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String = ""
}

class AbstractFfmpegExecutorTest : ShouldSpec() {
    private val processBuilder: ProcessBuilder = mock()
    private val process: Process = mock()
    private val sink: Sink = mock()
    private lateinit var processStdOutWriter: PipedOutputStream
    private lateinit var processStdOut: InputStream

    private val ffmpegExecutor = TestableAbstractFfmpegExecutor(processBuilder)

    override fun beforeSpec(description: Description, spec: Spec) {
        super.beforeSpec(description, spec)

        processStdOutWriter = PipedOutputStream()
        processStdOut = PipedInputStream(processStdOutWriter)
        whenever(process.inputStream).thenReturn(processStdOut)
        whenever(processBuilder.start()).thenReturn(process)
    }

    init {
        "before ffmpeg is launched" {
            "getExitCode" {
                should("return null") {
                    ffmpegExecutor.getExitCode() shouldBe null
                }
            }
            "isHealthy" {
                should("return false") {
                    ffmpegExecutor.isHealthy() shouldBe false
                }
            }
        }

        "after ffmpeg is launched" {
            whenever(process.isAlive).thenReturn(true)
            ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
            "if the process is alive" {
                "getExitCode" {
                    should("return null") {
                        ffmpegExecutor.getExitCode() shouldBe null
                    }
                }
                "and ffmpeg is encoding" {
                    processStdOutWriter.write("frame=24\n".toByteArray())
                    "isHealthy" {
                        should("return true") {
                            eventually(Duration.ofSeconds(5)) {
                                ffmpegExecutor.isHealthy() shouldBe true
                            }
                        }
                    }
                }
                "and ffmpeg has a warning" {
                    processStdOutWriter.write("Past duration 0.53 too large".toByteArray())
                    "isHealthy" {
                        should("return true") {
                            eventually(Duration.ofSeconds(5)) {
                                ffmpegExecutor.isHealthy() shouldBe true
                            }
                        }
                    }
                }
            }
            "if the process dies" {
                whenever(process.isAlive).thenReturn(false)
                "getExitCode" {
                    whenever(process.exitValue()).thenReturn(42)
                    should("return its exit code") {
                        ffmpegExecutor.getExitCode() shouldBe 42
                    }
                }
            }
        }
    }
}
