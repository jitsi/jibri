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

package org.jitsi.jibri.util

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

class ProcessMonitorTest : ShouldSpec() {
    private val process: MonitorableProcess = mock()
    private val exitCodes: MutableList<Int?> = mutableListOf()
    private val deadCallback: (exitCode: Int?) -> Unit = { exitCode ->
        exitCodes.add(exitCode)
    }
    private val processMonitor = ProcessMonitor(process, deadCallback)

    init {
        "when the monitored process dies" {
            reset(process)
            exitCodes.clear()
            whenever(process.isHealthy()).thenReturn(false)
            whenever(process.getExitCode()).thenReturn(42)
            processMonitor.run()
            "the callback" {
                should("be invoked") {
                    exitCodes.size shouldBe 1
                }
            }
            "the exit code" {
                should("have been passed correctly") {
                    exitCodes.first() shouldBe 42
                }
            }
        }
        "when the monitored process is still alive" {
            reset(process)
            exitCodes.clear()
            whenever(process.isHealthy()).thenReturn(true)
            whenever(process.getExitCode()).thenReturn(null)
            processMonitor.run()
            "the callback" {
                should("not be invoked") {
                    exitCodes.size shouldBe 0
                }
            }
        }
    }
}
