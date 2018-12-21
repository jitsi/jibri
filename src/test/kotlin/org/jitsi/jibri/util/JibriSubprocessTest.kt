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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.Spec
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldNotBeEmpty
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

internal class JibriSubprocessTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true
    private val processFactory: ProcessFactory = mock()
    private val processWrapper: ProcessWrapper = mock()
    private val processStatePublisher: ProcessStatePublisher = mock()
    private val subprocess = JibriSubprocess("name", mock(), processFactory, { _ -> processStatePublisher })
    private val processStateHandler = argumentCaptor<(ProcessState) -> Unit>()
    private val executorStateUpdates = mutableListOf<ProcessState>()

    override fun beforeSpec(description: io.kotlintest.Description, spec: Spec) {
        super.beforeSpec(description, spec)

        LoggingUtils.logOutput = { _, _ -> mock() }

        whenever(processFactory.createProcess(any(), any(), any())).thenReturn(processWrapper)
        whenever(processStatePublisher.addStatusHandler(processStateHandler.capture())).thenAnswer { }

        subprocess.addStatusHandler { status ->
            executorStateUpdates.add(status)
        }
    }

    init {
        "launching the subprocess" {
            "without any error launching the process" {
                subprocess.launch(listOf())
                should("not publish any state until the proc does") {
                    executorStateUpdates.shouldBeEmpty()
                }
                "when the process publishes a state" {
                    val procState = ProcessState(ProcessRunning(), "most recent output")
                    processStateHandler.firstValue(procState)
                    should("bubble up the state update") {
                        executorStateUpdates.shouldNotBeEmpty()
                        executorStateUpdates[0] shouldBe procState
                    }
                }
            }
            "and the start process throwing" {
                whenever(processWrapper.start()).thenAnswer { throw Exception() }
                subprocess.launch(listOf())
                should("publish a state update with the error") {
                    executorStateUpdates.shouldNotBeEmpty()
                    executorStateUpdates[0].runningState.shouldBeInstanceOf<ProcessFailedToStart>()
                }
            }
        }
        "calling stop before launch" {
            should("not cause any errors") {
                subprocess.stop()
            }
        }
    }
}
