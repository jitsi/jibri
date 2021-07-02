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
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.ffmpeg.FfmpegFailedToStart
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.error.JibriError
import org.jitsi.jibri.helpers.SeleniumMockHelper
import org.jitsi.jibri.helpers.createFinalizeProcessMock
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.FailedToJoinCall
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.ProcessFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

@Suppress("BlockingMethodInNonBlockingContext")
internal class FileRecordingJibriServiceTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val fs = MemoryFileSystemBuilder.newLinux().build()

    private val callParams = CallParams(
        CallUrlInfo("baseUrl", "callName")
    )
    private val callLoginParams = XmppCredentials(
        "domain",
        8080,
        "username",
        "password"
    )
    private val recordingsDir = fs.getPath("/tmp/recordings")
    private val sessionId = "session_id"
    private val additionalMetadata: Map<Any, Any> = mapOf(
        "token" to "my_token",
        "other_info" to "info"
    )
    private val fileRecordingParams = FileRecordingParams(
        callParams,
        sessionId,
        callLoginParams,
        additionalMetadata
    )
    private val seleniumMockHelper = SeleniumMockHelper()
    private val capturerMockHelper = CapturerMockHelper()
    private val processFactory: ProcessFactory = mockk()
    private val statusUpdates = mutableListOf<ComponentState>()
    private val fileRecordingJibriService = FileRecordingJibriService(
        fileRecordingParams,
        seleniumMockHelper.mock,
        capturerMockHelper.mock,
        processFactory,
        fs
    ).also {
        it.addStatusHandler(statusUpdates::add)
    }

    init {
        context("when the recording directory can't be created") {
            Files.createDirectories(recordingsDir)
            setPerms("r--r--r--", recordingsDir)

            context("starting a file recording service") {
                fileRecordingJibriService.start()

                should("publish an error status") {
                    statusUpdates shouldHaveSize 1
                    val status = statusUpdates.first()

                    status.shouldBeInstanceOf<ComponentState.Error>()
                    status.error shouldBe ErrorCreatingRecordingsDirectory
                }
            }
        }
        context("starting a file recording service") {
            fileRecordingJibriService.start()
            should("create the recording directory") {
                Files.exists(fs.getPath(recordingsDir.toString(), sessionId)) shouldBe true
            }
            should("have selenium join the call") {
                verify { seleniumMockHelper.mock.joinCall(any(), any()) }
            }
            context("and selenium joins the call successfully") {
                seleniumMockHelper.startSuccessfully()
                should("start the capturer") {
                    verify { capturerMockHelper.mock.start(any()) }
                }
                should("pass the correct arguments to the capturer's sink") {
                    capturerMockHelper.sink.path.shouldContain(recordingsDir.toString())
                    capturerMockHelper.sink.path.shouldContain(callParams.callUrlInfo.callName)
                }
                context("and the capturer starts successfully") {
                    capturerMockHelper.startSuccessfully()

                    should("publish that it's running") {
                        statusUpdates shouldHaveSize 1
                        val status = statusUpdates.first()

                        status shouldBe ComponentState.Running
                    }
                }
                context("but the capturer fails to start") {
                    capturerMockHelper.error(FfmpegFailedToStart)

                    should("publish an error") {
                        statusUpdates shouldHaveSize 1
                        val status = statusUpdates.first()

                        status.shouldBeInstanceOf<ComponentState.Error>()
                    }
                }
            }

            context("but joining the call fails") {
                seleniumMockHelper.error(FailedToJoinCall)

                should("publish an error") {
                    statusUpdates shouldHaveSize 1
                    val status = statusUpdates.first()

                    status.shouldBeInstanceOf<ComponentState.Error>()
                }
            }
        }
        context("stopping a service which has successfully started") {
            // First get the service in a 'successful start' state.
            fileRecordingJibriService.start()
            seleniumMockHelper.startSuccessfully()
            capturerMockHelper.startSuccessfully()

            // Validate that it started
            statusUpdates shouldHaveSize 1
            val status = statusUpdates.first()
            status shouldBe ComponentState.Running

            context("where no media file was written") {
                fileRecordingJibriService.stop()
                should("not run the finalize scripts") {
                    verify(exactly = 0) { processFactory.createProcess(any(), any()) }
                }
                should("delete the directory") {
                    Files.exists(fs.getPath(recordingsDir.toString(), sessionId)) shouldBe false
                }
                should("still tell selenium to leave the call") {
                    verify { seleniumMockHelper.mock.leaveCallAndQuitBrowser() }
                }
            }

            context("where a media file was written") {
                Files.createFile(fs.getPath(capturerMockHelper.sink.path))
                every { seleniumMockHelper.mock.getParticipants() } returns listOf(mapOf("a" to "b"))
                val finalizeProcessMock = createFinalizeProcessMock(true)
                every {
                    processFactory.createProcess(match { it.first().contains("finalize") }, any(), any(), any())
                } returns finalizeProcessMock

                fileRecordingJibriService.stop()

                should("get the list of participants from selenium") {
                    verify { seleniumMockHelper.mock.getParticipants() }
                }
                should("write the metadata file") {
                    Files.exists(fs.getPath(recordingsDir.toString(), sessionId, "metadata.json")) shouldBe true
                }
                should("still tell selenium to leave the call") {
                    verify { seleniumMockHelper.mock.leaveCallAndQuitBrowser() }
                }
                should("run the finalize command") {
                    verify { finalizeProcessMock.start() }
                    verify { finalizeProcessMock.waitFor() }
                }
            }
        }
    }

    private fun setPerms(permsStr: String, p: Path) {
        val perms = PosixFilePermissions.fromString(permsStr)
        Files.setPosixFilePermissions(p, perms)
    }
}

// This wraps a mock of a Capturer, and does some setup/exposes some helpers to make its use more ergonomic
private class CapturerMockHelper {
    private val sinkSlot = slot<Sink>()
    private val eventHandlers = mutableListOf<(ComponentState) -> Boolean>()

    val mock: FfmpegCapturer = mockk(relaxed = true) {
        every { addStatusHandler(captureLambda()) } answers {
            // This behavior mimics what's done in StatusPublisher#addStatusHandler
            eventHandlers.add {
                lambda<(ComponentState) -> Unit>().captured(it)
                true
            }
        }

        every { start(capture(sinkSlot)) } just Runs
    }

    val sink: Sink
        get() = sinkSlot.captured

    fun startSuccessfully() {
        eventHandlers.forEach { it(ComponentState.Running) }
    }

    fun error(error: JibriError) {
        eventHandlers.forEach { it(ComponentState.Error(error)) }
    }
}
