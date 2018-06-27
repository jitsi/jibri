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

package org.jitsi.jibri.service.impl

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.matchers.string.contain
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.ProcessFactory
import org.jitsi.jibri.util.ProcessWrapper
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ScheduledExecutorService

internal class FileRecordingJibriServiceTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true

    private fun setPerms(permsStr: String, p: Path) {
        val perms = PosixFilePermissions.fromString(permsStr)
        Files.setPosixFilePermissions(p, perms)
    }

    private val fs = MemoryFileSystemBuilder.newLinux().build()

    private val callParams = CallParams(
        CallUrlInfo("baseUrl", "callName")
    )
    private val callLoginParams = XmppCredentials(
        "domain",
        "username",
        "password"
    )
    private val recordingsDir = fs.getPath("/path/to/recordings")
    private val finalizeScript = fs.getPath("/path/to/finalize")
    private val sessionId = "session_id"
    private val fileRecordingParams = FileRecordingParams(
        callParams,
        sessionId,
        callLoginParams,
        finalizeScript,
        recordingsDir
    )
    private val executor: ScheduledExecutorService = mock()
    private val jibriSelenium: JibriSelenium = mock()
    private val capturer: Capturer = mock()
    private val sinkCapturer = argumentCaptor<Sink>()
    private val processFactory: ProcessFactory = mock()

    private val fileRecordingJibriService = FileRecordingJibriService(
        fileRecordingParams,
        executor,
        jibriSelenium,
        capturer,
        processFactory
    )

    init {
        "start" {
            "when creating the recording directory fails" {
                Files.createDirectories(recordingsDir)
                setPerms("r--r--r--", recordingsDir)
                should("return false") {
                    fileRecordingJibriService.start() shouldBe false
                }
            }
            "when joining the call succeeds" {
                whenever(jibriSelenium.joinCall(any(), any())).thenReturn(true)
                "and the capturer starts successfully" {
                    whenever(capturer.start(sinkCapturer.capture())).thenReturn(true)
                    "and the recordings directory doesn't exist" {
                        val startResult = fileRecordingJibriService.start()
                        should("start the capturer") {
                            verify(capturer).start(any())
                        }
                        should("have selenium join the call") {
                            verify(jibriSelenium).joinCall(any(), any())
                        }
                        should("return true") {
                            startResult shouldBe true
                        }
                        should("create the recording directory") {
                            Files.exists(recordingsDir) shouldBe true
                        }
                        should("start the capturer with a sink wtih the right filename") {
                            sinkCapturer.firstValue.path should contain(recordingsDir.toString())
                            sinkCapturer.firstValue.path should contain(callParams.callUrlInfo.callName)
                        }
                    }
                    "and the recordings directory exists" {
                        Files.createDirectories(recordingsDir)
                        fileRecordingJibriService.start() shouldBe true
                    }
                    "and the recordings directory exists but isn't writable" {
                        Files.createDirectories(recordingsDir)
                        setPerms("r--r--r--", recordingsDir)
                        should("return false") {
                            fileRecordingJibriService.start() shouldBe false
                        }
                    }
                }
                "and the capturer doesn't start" {
                    whenever(capturer.start(sinkCapturer.capture())).thenReturn(false)
                    should("return false") {
                        fileRecordingJibriService.start() shouldBe false
                    }
                }
            }
            "when joining the call fails" {
                whenever(jibriSelenium.joinCall(any(), any())).thenReturn(false)
                should("return false") {
                    fileRecordingJibriService.start() shouldBe false
                }
            }
        }
        "stop" {
            "after a successful start" {
                whenever(jibriSelenium.joinCall(any(), any())).thenReturn(true)
                whenever(jibriSelenium.getParticipants()).thenReturn(listOf())
                whenever(capturer.start(any())).thenReturn(true)

                fileRecordingJibriService.start()
                val recordingFile = recordingsDir.resolve(sessionId).resolve("recording.mp4")
                Files.createFile(recordingFile)

                val setupFinalizeProcessMock = { shouldSucceed: Boolean ->
                    val finalizeProc: ProcessWrapper = mock()
                    val op = PipedOutputStream()
                    val stdOut = PipedInputStream(op)
                    whenever(finalizeProc.getOutput()).thenReturn(stdOut)
                    whenever(finalizeProc.waitFor()).thenReturn(if (shouldSucceed) 0 else 1)
                    whenever(finalizeProc.exitValue).thenReturn(if (shouldSucceed) 0 else 1)
                    whenever(finalizeProc.start()).thenAnswer {
                        Files.exists(recordingsDir.resolve(sessionId).resolve("metadata.json")) shouldBe true
                        op.close()
                    }
                    finalizeProc
                }

                "regardless of whether or not finalize succeeds or fails" {
                    // This is code not dependent on finalize's return code, but we have to make it return
                    // something so we'll use success
                    val finalizeProc = setupFinalizeProcessMock(true)
                    val finalizeProcPath = argumentCaptor<List<String>>()
                    whenever(processFactory.createProcess(finalizeProcPath.capture(), anyOrNull(), any()))
                        .thenReturn(finalizeProc)

                    fileRecordingJibriService.stop()
                    should("stop the capturer") {
                        verify(capturer).stop()
                    }
                    should("have selenium leave the call") {
                        verify(jibriSelenium).leaveCallAndQuitBrowser()
                    }
                    should("call the finalize script with the correct arguments") {
                        println(finalizeProcPath.firstValue)
                        finalizeProcPath.firstValue[0] shouldBe finalizeScript.toString()
                        finalizeProcPath.firstValue[1] shouldBe recordingsDir.resolve(sessionId).toString()
                    }
                }
            }
        }
    }
}
