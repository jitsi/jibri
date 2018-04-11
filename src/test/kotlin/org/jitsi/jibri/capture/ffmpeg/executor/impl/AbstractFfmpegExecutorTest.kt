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
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.helpers.always
import org.jitsi.jibri.helpers.eventually
import org.jitsi.jibri.sink.Sink
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration

class AbstractFfmpegExecutorTest : FunSpec() {
    override fun isInstancePerTest(): Boolean = true
    class TestableAbstractFfmpegExecutor(fakeProcessBuilder: ProcessBuilder) : AbstractFfmpegExecutor(fakeProcessBuilder) {
        override fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String = ""
    }

    private val processBuilder: ProcessBuilder = mock()
    private val process: Process = mock()
    private val sink: Sink = mock()
    private lateinit var processStdOutWriter: PipedOutputStream
    private lateinit var processStdOut: InputStream
    private lateinit var ffmpegExecutor: TestableAbstractFfmpegExecutor

    override fun beforeTest(description: Description) {
        super.beforeTest(description)

        whenever(processBuilder.start()).thenReturn(process)
        processStdOutWriter = PipedOutputStream()
        processStdOut = PipedInputStream(processStdOutWriter)
        whenever(process.inputStream).thenReturn(processStdOut)

        ffmpegExecutor = TestableAbstractFfmpegExecutor(processBuilder)
    }

    private fun setProcessStdOutLine(line: String) {
        val lineToWrite = if (line.endsWith("\n")) line else "$line\n"
        processStdOutWriter.write(lineToWrite.toByteArray())
    }

    init {
        test("before ffmpeg is launched, getExitCode and isHealthy should act accordingly") {
            ffmpegExecutor.getExitCode() shouldBe null
            ffmpegExecutor.isHealthy() shouldBe false
        }

        test("after ffmpeg is launched and is alive, getExitCode should return null") {
            ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
            whenever(process.isAlive).thenReturn(true)
            ffmpegExecutor.getExitCode() shouldBe null
        }

        test("after ffmpeg is launched and is encoding, isHealthy should return true") {
            ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
            whenever(process.isAlive).thenReturn(true)
            setProcessStdOutLine("frame=24")
            eventually(Duration.ofSeconds(5)) {
                ffmpegExecutor.isHealthy() shouldBe true
            }
        }

        test("after ffmpeg is launched and has a warning, isHealthy should return true") {
            ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
            whenever(process.isAlive).thenReturn(true)
            setProcessStdOutLine("Past duration 0.53 too large")
            eventually(Duration.ofSeconds(5)) {
                ffmpegExecutor.isHealthy() shouldBe true
            }
        }

        test("after ffmpeg is launched and has an error, isHealthy should return false") {
            ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
            setProcessStdOutLine("Error")
            whenever(process.isAlive).thenReturn(true)
            always(Duration.ofSeconds(5)) {
                ffmpegExecutor.isHealthy() shouldBe false
            }
        }

        test("if the process dies, its exit code is returned") {
            ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
            whenever(process.isAlive).thenReturn(false)
            whenever(process.exitValue()).thenReturn(42)
            ffmpegExecutor.getExitCode() shouldBe 42
        }
    }
}
