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

package org.jitsi.jibri.util

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jitsi.jibri.helpers.resetOutputLogger
import org.jitsi.jibri.helpers.setTestOutputLogger

internal class JibriSubprocessTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val processFactory: ProcessFactory = mockk()
    private val processWrapper: ProcessWrapper = mockk(relaxed = true)
    private val processStatePublisher: ProcessStatePublisher = mockk(relaxed = true)
    @Suppress("MoveLambdaOutsideParentheses")
    private val subprocess = JibriSubprocess("name", mockk(), processFactory, { processStatePublisher })
    private val processStateHandler = slot<(ProcessState) -> Unit>()
    private val executorStateUpdates = mutableListOf<ProcessState>()

    init {
        beforeSpec {
            LoggingUtils.setTestOutputLogger { _, _ -> mockk(relaxed = true) }

            every { processFactory.createProcess(any(), any(), any()) } returns processWrapper
            every { processStatePublisher.addStatusHandler(capture(processStateHandler)) } just Runs

            subprocess.addStatusHandler { status ->
                executorStateUpdates.add(status)
            }
        }

        afterSpec {
            LoggingUtils.resetOutputLogger()
        }
        context("launching the subprocess") {
            context("without any error launching the process") {
                subprocess.launch(listOf())
                should("not publish any state until the proc does") {
                    executorStateUpdates.shouldBeEmpty()
                }
                context("when the process publishes a state") {
                    val procState = ProcessState(ProcessRunning(), "most recent output")
                    processStateHandler.captured(procState)
                    should("bubble up the state update") {
                        executorStateUpdates.shouldNotBeEmpty()
                        executorStateUpdates[0] shouldBe procState
                    }
                }
            }
            context("and the start process throwing") {
                every { processWrapper.start() } throws Exception()
                subprocess.launch(listOf())
                should("publish a state update with the error") {
                    executorStateUpdates.shouldNotBeEmpty()
                    executorStateUpdates[0].runningState.shouldBeInstanceOf<ProcessFailedToStart>()
                }
            }
        }
        context("stopping the subprocess") {
            context("before launch") {
                should("not cause any errors") {
                    subprocess.stop()
                }
            }
            context("after it launches") {
                subprocess.launch(emptyList())
                context("when it refuses to stop gracefully") {
                    every { processWrapper.stopAndWaitFor(any()) } returns false
                    should("try and destroy it forcibly") {
                        subprocess.stop()
                        verify { processWrapper.destroyForciblyAndWaitFor(any()) }
                    }
                }
            }
        }
    }
}
