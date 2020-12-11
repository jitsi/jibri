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

package org.jitsi.jibri

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import org.jitsi.jibri.api.xmpp.XmppCredentials
import org.jitsi.jibri.job.IntermediateJobState
import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.StartingUp
import org.jitsi.jibri.job.streaming.StreamingParams
import org.jitsi.jibri.selenium.CallParams
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.hours
import kotlin.time.seconds

internal class JibriManagerTest : ShouldSpec() {
    private val jobFactory: JobFactory = mockk()
    private val jibriManager = JibriManager(jobFactory)

    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    init {
        context("When no job is running") {
            should("be in idle state") {
                jibriManager.currentState.value shouldBe JibriState.Idle
            }
        }

        context("executeWhenIdle") {
            context("when jibri is idle") {
                should("be run immediately") {
                    var idleFuncRun = false
                    jibriManager.executeWhenIdle { idleFuncRun = true }
                    idleFuncRun shouldBe true
                }
            }

            context("when jibri is busy") {
                val latch = CountDownLatch(1)
                useJob {
                    object : JibriJob {
                        override suspend fun run() {
                            latch.await()
                            throw object : JobCompleted("Done") {}
                        }
                        override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                        override val name: String = "test"
                    }
                }

                val session = startRecording()
                var idleFuncRun = false
                jibriManager.executeWhenIdle { idleFuncRun = true }

                should("run after the job is finished") {
                    idleFuncRun shouldBe false
                    latch.countDown()
                    shouldCompleteCleanly { session.await() }
                    idleFuncRun shouldBe true
                }
            }
        }

        context("Starting a job") {
            context("when another job is already running") {
                useJob {
                    object : JibriJob {
                        override suspend fun run() { completeCleanlyAfter(1.hours) }
                        override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                        override val name: String = "test"
                    }
                }

                startRecording()

                should("throw a busy exception") {
                    shouldThrow<JibriBusy> {
                        startRecording()
                    }
                }
            }

            context("which finishes cleanly") {
                useJob {
                    object : JibriJob {
                        override suspend fun run() { completeCleanlyAfter(1.seconds) }
                        override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                        override val name: String = "test"
                    }
                }
                val session = startRecording()
                should("go to a busy state") {
                    jibriManager.currentState.value.shouldBeInstanceOf<JibriState.Busy>()
                }

                should("complete cleanly and go back to idle state") {
                    shouldCompleteCleanly { session.await() }
                    jibriManager.currentState.value shouldBe JibriState.Idle
                }
            }

            context("which throws an error while running") {
                useJob {
                    object : JibriJob {
                        override suspend fun run() {
                            throw Exception("boom")
                        }
                        override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                        override val name: String = "test"
                    }
                }
                val session = startRecording()
                should("bubble up the error and go back to idle state") {
                    shouldThrow<Exception> {
                        session.await()
                    }.message shouldBe "boom"

                    jibriManager.currentState.value shouldBe JibriState.Idle
                }
            }

            context("which throws a system error while starting up") {
                every { jobFactory.createRecordingJob(any(), any(), any(), any(), any(), any()) } throws
                    RecordingsDirectoryNotWritable("/a/b/c")

                should("bubble up the error and go into an error state") {
                    shouldThrow<RecordingsDirectoryNotWritable> {
                        startRecording()
                    }
                    jibriManager.currentState.value.shouldBeInstanceOf<JibriState.Error>()
                }
            }

            context("which throws a request error while starting up") {
                every { jobFactory.createStreamingJob(any(), any(), any(), any()) } throws RtmpUrlNotAllowed("blah")

                should("bubble up the error and go back to idle state") {
                    shouldThrow<RtmpUrlNotAllowed> {
                        startStreaming()
                    }
                    jibriManager.currentState.value shouldBe JibriState.Idle
                }
            }

            context("which gets timed out") {
                useJob {
                    object : JibriJob {
                        override suspend fun run() { completeCleanlyAfter(1.hours) }
                        override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                        override val name: String = "test"
                    }
                }

                val session = startRecording(timeout = 1.seconds)

                should("be cancelled after the timeout") {
                    withTimeout(2.seconds) {
                        shouldThrow<CancellationException> {
                            session.await()
                        }
                    }
                }
            }

            context("which, while running,") {
                useJob {
                    object : JibriJob {
                        override suspend fun run() { completeCleanlyAfter(1.hours) }
                        override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                        override val name: String = "test"
                    }
                }

                val session = startRecording()

                context("is cancelled externally") {
                    session.cancel("Stopping")
                    should("bubble up the error and go back to idle state") {
                        shouldThrow<CancellationException> {
                            session.await()
                        }
                        jibriManager.currentState.value shouldBe JibriState.Idle
                    }
                }

                context("is cancelled due to the jibri shutting down") {
                    jibriManager.shutdown()
                    shouldThrow<CancellationException> {
                        session.await()
                    }
                }
            }

            context("when in single-use mode") {
                withConfig("jibri.single-use-mode=true") {
                    useJob {
                        object : JibriJob {
                            override suspend fun run() { completeCleanlyAfter(1.seconds) }
                            override val state: StateFlow<IntermediateJobState> = MutableStateFlow(StartingUp)
                            override val name: String = "test"
                        }
                    }

                    val session = startRecording()
                    should("finish and go to expired state") {
                        shouldCompleteCleanly { session.await() }
                        jibriManager.currentState.value shouldBe JibriState.Expired
                    }
                }
            }
        }
    }

    private fun useJob(block: () -> JibriJob) {
        val job = block()
        every { jobFactory.createRecordingJob(any(), any(), any(), any(), any(), any()) } returns job
    }

    private fun startRecording(timeout: Duration = 0.seconds, context: EnvironmentContext? = null): JibriSession {
        return jibriManager.startFileRecordingSession(
            JobParams(
                usageTimeout = timeout,
                sessionId = "12345",
                callParams = CallParams(
                    CallUrlInfo("blah", "blah"),
                    XmppCredentials("domain", "username", "password")
                ),
            ),
            context
        )
    }

    private fun startStreaming(timeout: Duration = 0.seconds, context: EnvironmentContext? = null): JibriSession {
        return jibriManager.startStreamingSession(
            JobParams(
                usageTimeout = timeout,
                sessionId = "12345",
                callParams = CallParams(
                    CallUrlInfo("blah", "blah"),
                    XmppCredentials("domain", "username", "password")
                ),
            ),
            StreamingParams(
                rtmpUrl = "rtmp://blah"
            ),
            context
        )
    }
}

private suspend fun completeCleanlyAfter(duration: Duration) {
    delay(duration)
    throw object : JobCompleted("Done") {}
}

suspend fun shouldCompleteCleanly(block: suspend () -> Unit) {
    shouldThrow<JobCompleted> {
        block()
    }
}
