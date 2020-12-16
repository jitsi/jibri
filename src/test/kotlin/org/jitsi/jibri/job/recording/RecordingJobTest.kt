/*
 * Copyright @ 2018 - present 8x8, Inc.
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

package org.jitsi.jibri.job.recording

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.jitsi.jibri.BadRtmpUrl
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.EmptyCallException
import org.jitsi.jibri.ErrorCreatingRecordingsDirectory
import org.jitsi.jibri.FailedToJoinCall
import org.jitsi.jibri.ProcessFailedToStart
import org.jitsi.jibri.ProcessHung
import org.jitsi.jibri.RecordingsDirectoryNotWritable
import org.jitsi.jibri.api.xmpp.XmppCredentials
import org.jitsi.jibri.job.Running
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.Selenium
import org.jitsi.jibri.selenium.SeleniumFactory
import org.jitsi.jibri.util.EOF
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.runProcess
import org.jitsi.jibri.util.stopProcess
import org.jitsi.jibri.withConfig
import org.jitsi.utils.logging2.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

@Suppress("BlockingMethodInNonBlockingContext")
class RecordingJobTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val fs = MemoryFileSystemBuilder.newLinux().build()
    private val recordingsDir = fs.getPath("/path/to/recordings")
    private val logger: Logger = mockk(relaxed = true)
    private val selenium: Selenium = mockk(relaxed = true)
    private val seleniumFactory: SeleniumFactory = mockk {
        every { create(any()) } returns selenium
    }

    override fun beforeSpec(spec: Spec) {
        Files.createDirectories(recordingsDir)
        mockkStatic("org.jitsi.jibri.util.ProcessWrapperKt")
    }

    init {
        context("A recording job") {
            context("when the recordings directory can't be created") {
                setPerms("r--r--r--", recordingsDir)

                shouldThrow<ErrorCreatingRecordingsDirectory> {
                    withConfig("jibri.recording.recordings-directory=/path/to/recordings") {
                        createRecordingJob()
                    }
                }
            }

            context("when the recordings directory isn't writable") {
                val sessionId = "sessionId"
                val sessionDir = recordingsDir.resolve(sessionId)
                setPerms("rwxrwxrwx", recordingsDir)
                // In reality this would happen if the umask wasn't set right, but there isn't an API to set that so
                // we create the session recording directory ahead of time and change the permissions on it to simulate
                // that
                Files.createDirectories(sessionDir)
                setPerms("r--r--r--", sessionDir)
                shouldThrow<RecordingsDirectoryNotWritable> {
                    withConfig("jibri.recording.recordings-directory=/path/to/recordings") {
                        createRecordingJob(sessionId = sessionId)
                    }
                }
            }

            context("when selenium fails to join the call") {
                val job = createRecordingJob()
                every { selenium.joinCall(any(), any()) } throws FailedToJoinCall

                should("bubble up the exception and cleanup") {
                    shouldThrow<FailedToJoinCall> {
                        job.run()
                    }
                    verify { selenium.leaveCallAndQuitBrowser() }
                }
            }

            context("when a call check fails") {
                val (ffmpeg, _) = createMockProcessWrapper("ffmpeg")
                every { runProcess(ffmpegCommand(), any(), any()) } returns ffmpeg

                val job = createRecordingJob()

                coEvery { selenium.monitorCall() } throws EmptyCallException
                should("bubble up the exception and cleanup") {
                    shouldThrow<EmptyCallException> {
                        job.run()
                    }
                    verify { selenium.leaveCallAndQuitBrowser() }
                    verify { stopProcess(ffmpeg, any()) }
                }
            }

            context("when ffmpeg starts up") {
                val (ffmpeg, ffmpegOutput) = createMockProcessWrapper("ffmpeg")
                every { runProcess(ffmpegCommand(), any(), any()) } returns ffmpeg

                val job = createRecordingJob()

                ffmpegOutput.emit("frame=   15 fps=0.0 q=32.0 size=      55kB time=00:00:00.76 " +
                    "bitrate= 583.3kbits/s speed=1.46x")

                should("change its state to running") {
                    runBlockingTest {
                        val session = launch {
                            shouldThrowAny {
                                job.run()
                            }
                        }
                        advanceUntilIdle()
                        job.state.value shouldBe Running
                        session.cancel()
                    }
                }
            }

            context("when ffmpeg has an error") {
                val (ffmpeg, ffmpegOutput) = createMockProcessWrapper("ffmpeg")
                every { runProcess(ffmpegCommand(), any(), any()) } returns ffmpeg

                val job = createRecordingJob()

                ffmpegOutput.emit("rtmp://blah Input/output error")
                should("bubble up the exception and cleanup") {
                    shouldThrow<BadRtmpUrl> {
                        job.run()
                    }
                    verify { selenium.leaveCallAndQuitBrowser() }
                    verify { stopProcess(ffmpeg, any()) }
                }
            }

            context("when ffmpeg fails to start") {
                every { runProcess(ffmpegCommand(), any(), any()) } throws ProcessFailedToStart("boom")

                val job = createRecordingJob()

                should("bubble up the exception and cleanup") {
                    shouldThrow<ProcessFailedToStart> {
                        job.run()
                    }
                    verify { selenium.leaveCallAndQuitBrowser() }
                }
            }

            context("when ffmpeg hangs") {
                val (ffmpeg, ffmpegOutput) = createMockProcessWrapper("ffmpeg")
                every { runProcess(ffmpegCommand(), any(), any()) } returns ffmpeg

                val job = createRecordingJob()

                // We need to emit at least one thing to trigger the hang detection
                ffmpegOutput.emit("Starting up")
                should("bubble up the exception and cleanup") {
                    shouldThrow<ProcessHung> {
                        job.run()
                    }
                    verify { selenium.leaveCallAndQuitBrowser() }
                    verify { stopProcess(ffmpeg, any()) }
                }
            }

            context("when the finalize script has an error") {
                withConfig("jibri.recording.finalize-script=finalize.sh") {
                    val (ffmpeg, _) = createMockProcessWrapper("ffmpeg")
                    every { runProcess(ffmpegCommand(), any(), any()) } returns ffmpeg
                    every { runProcess(processCommand("finalize.sh"), any(), any()) } throws IOException("boom")

                    val job = createRecordingJob()

                    // Something to cause the session to end
                    coEvery { selenium.monitorCall() } throws EmptyCallException

                    should("still cleanup correctly") {
                        shouldThrowAny {
                            job.run()
                        }
                        verify { selenium.leaveCallAndQuitBrowser() }
                        verify { stopProcess(ffmpeg, any()) }
                    }
                }
            }
        }
    }

    /**
     * A helper to create a recording job with the stubbed values to save space in tests
     */
    private fun createRecordingJob(
        sessionId: String = "sessionId"
    ): RecordingJob {
        return RecordingJob(
            logger,
            sessionId,
            dummyCallParams,
            seleniumFactory = seleniumFactory,
            fileSystem = fs
        )
    }
}

private fun MockKMatcherScope.ffmpegCommand() = processCommand("ffmpeg")

/**
 * A Mockk matcher which matches a command (List<String>) to a process by checking that the first element in the list
 * matches the given process name.
 */
private fun MockKMatcherScope.processCommand(processName: String) = match<List<String>> {
    it.first() == processName
}

private fun createMockProcessWrapper(processName: String): Pair<ProcessWrapper, MutableSharedFlow<String>> {
    val outputFlow = MutableSharedFlow<String>(replay = 50)
    val process: ProcessWrapper = mockk {
        every { output } returns outputFlow
        every { name } returns processName
    }
    every { stopProcess(process) } coAnswers {
        outputFlow.emit(EOF)
    }

    return process to outputFlow
}

private val dummyCallParams = CallParams(
    callUrlInfo = CallUrlInfo(
        baseUrl = "blah",
        callName = "name"
    ),
    callLogin = XmppCredentials(
        domain = "domain",
        username = "username",
        password = "password"
    )
)

/**
 * Helper to set file permissions for a given [Path]
 */
private fun setPerms(permsStr: String, p: Path) {
    val perms = PosixFilePermissions.fromString(permsStr)
    Files.setPosixFilePermissions(p, perms)
}
