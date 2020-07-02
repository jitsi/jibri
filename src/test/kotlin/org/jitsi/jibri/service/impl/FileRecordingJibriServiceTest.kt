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

// internal class FileRecordingJibriServiceTest : ShouldSpec() {
//    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf
//
//    private fun setPerms(permsStr: String, p: Path) {
//        val perms = PosixFilePermissions.fromString(permsStr)
//        Files.setPosixFilePermissions(p, perms)
//    }
//
//    private val fs = MemoryFileSystemBuilder.newLinux().build()
//
//    private val callParams = CallParams(
//        CallUrlInfo("baseUrl", "callName")
//    )
//    private val callLoginParams = XmppCredentials(
//        "domain",
//        "username",
//        "password"
//    )
//    private val recordingsDir = fs.getPath("/path/to/recordings")
//    private val finalizeScript = fs.getPath("/path/to/finalize")
//    private val sessionId = "session_id"
//    private val additionalMetadata: Map<Any, Any> = mapOf(
//        "token" to "my_token",
//        "other_info" to "info"
//    )
//    private val fileRecordingParams = FileRecordingParams(
//        callParams,
//        sessionId,
//        callLoginParams,
//        finalizeScript,
//        recordingsDir,
//        additionalMetadata
//    )
//    private val executor: ScheduledExecutorService = mock()
//    private val jibriSelenium: JibriSelenium = mock()
//    private val capturer: Capturer = mock()
//    private val sinkCapturer = argumentCaptor<Sink>()
//    private val processFactory: ProcessFactory = mock()
//
//    private val fileRecordingJibriService = FileRecordingJibriService(
//        fileRecordingParams,
//        jibriSelenium,
//        capturer,
//        processFactory
//    )
//
//    override fun beforeTest(description: Description) {
//        TaskPools.recurringTasksPool = executor
//    }
//
//    init {
//        "start" {
//            "when creating the recording directory fails" {
//                Files.createDirectories(recordingsDir)
//                setPerms("r--r--r--", recordingsDir)
//                should("return false") {
//                    fileRecordingJibriService.start() shouldBe false
//                }
//            }
//            "when joining the call succeeds" {
//                whenever(jibriSelenium.joinCall(any(), any())).thenReturn(true)
//                "and the capturer starts successfully" {
//                    whenever(capturer.start(sinkCapturer.capture())).thenReturn(true)
//                    "and the recordings directory doesn't exist" {
//                        val startResult = fileRecordingJibriService.start()
//                        should("start the capturer") {
//                            verify(capturer).start(any())
//                        }
//                        should("have selenium join the call") {
//                            verify(jibriSelenium).joinCall(any(), any())
//                        }
//                        should("return true") {
//                            startResult shouldBe true
//                        }
//                        should("create the recording directory") {
//                            Files.exists(recordingsDir) shouldBe true
//                        }
//                        should("start the capturer with a sink wtih the right filename") {
//                            sinkCapturer.firstValue.path.shouldContain(recordingsDir.toString())
//                            sinkCapturer.firstValue.path.shouldContain(callParams.callUrlInfo.callName)
//                        }
//                    }
//                    "and the recordings directory exists" {
//                        Files.createDirectories(recordingsDir)
//                        fileRecordingJibriService.start() shouldBe true
//                    }
//                    "and the recordings directory exists but isn't writable" {
//                        Files.createDirectories(recordingsDir)
//                        setPerms("r--r--r--", recordingsDir)
//                        should("return false") {
//                            fileRecordingJibriService.start() shouldBe false
//                        }
//                    }
//                }
//                "and the capturer doesn't start" {
//                    whenever(capturer.start(sinkCapturer.capture())).thenReturn(false)
//                    should("return false") {
//                        fileRecordingJibriService.start() shouldBe false
//                    }
//                }
//            }
//            "when joining the call fails" {
//                whenever(jibriSelenium.joinCall(any(), any())).thenReturn(false)
//                should("return false") {
//                    fileRecordingJibriService.start() shouldBe false
//                }
//            }
//        }
//        "stop" {
//            "after a successful start" {
//                whenever(jibriSelenium.joinCall(any(), any())).thenReturn(true)
//                whenever(jibriSelenium.getParticipants()).thenReturn(listOf())
//                whenever(capturer.start(any())).thenReturn(true)
//
//                fileRecordingJibriService.start()
//                val recordingFile = recordingsDir.resolve(sessionId).resolve("recording.mp4")
//                Files.createFile(recordingFile)
//
//                val setupFinalizeProcessMock = { shouldSucceed: Boolean ->
//                    val finalizeProc: ProcessWrapper = mock()
//                    val op = PipedOutputStream()
//                    val stdOut = PipedInputStream(op)
//                    whenever(finalizeProc.getOutput()).thenReturn(stdOut)
//                    whenever(finalizeProc.waitFor()).thenReturn(if (shouldSucceed) 0 else 1)
//                    whenever(finalizeProc.exitValue).thenReturn(if (shouldSucceed) 0 else 1)
//                    whenever(finalizeProc.start()).thenAnswer {
//                        Files.exists(recordingsDir.resolve(sessionId).resolve("metadata.json")) shouldBe true
//                        val metadataReader = Files.newBufferedReader(recordingsDir.resolve(sessionId).resolve("metadata.json"))
//                        val metaData: Map<Any, Any> = jacksonObjectMapper().readValue(metadataReader)
//                        metaData.shouldContainAll(mapOf<Any, Any>(
//                            "token" to "my_token",
//                            "other_info" to "info",
//                            "meeting_url" to "baseUrl/callName"
//                        ))
//                        op.close()
//                    }
//                    finalizeProc
//                }
//
//                "regardless of whether or not finalize succeeds or fails" {
//                    // This is code not dependent on finalize's return code, but we have to make it return
//                    // something so we'll use success
//                    val finalizeProc = setupFinalizeProcessMock(true)
//                    val finalizeProcPath = argumentCaptor<List<String>>()
//                    whenever(processFactory.createProcess(finalizeProcPath.capture(), anyOrNull(), any()))
//                        .thenReturn(finalizeProc)
//
//                    fileRecordingJibriService.stop()
//                    should("stop the capturer") {
//                        verify(capturer).stop()
//                    }
//                    should("have selenium leave the call") {
//                        verify(jibriSelenium).leaveCallAndQuitBrowser()
//                    }
//                    should("call the finalize script with the correct arguments") {
//                        finalizeProcPath.firstValue[0] shouldBe finalizeScript.toString()
//                        finalizeProcPath.firstValue[1] shouldBe recordingsDir.resolve(sessionId).toString()
//                    }
//                }
//            }
//        }
//    }
// }
