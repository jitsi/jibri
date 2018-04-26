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

import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream

internal class ProcessWrapperTest : ShouldSpec() {
    internal class ProcessWrapperImpl(
        command: List<String> = listOf(),
        environment: Map<String, String> = mapOf(),
        processBuilder: ProcessBuilder
    ) : ProcessWrapper<Boolean>(command, environment, processBuilder) {

        override fun getStatus(): Pair<Boolean, String> {
            return Pair(true, "status")
        }
    }

    private val processBuilder: ProcessBuilder = mock()
    private val process: Process = mock()
    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)

    init {
        whenever(process.inputStream).thenReturn(inputStream)
        whenever(processBuilder.start()).thenReturn(process)
        val processWrapper = ProcessWrapperImpl(listOf(), processBuilder = processBuilder)
        processWrapper.start()

        "getOutput" {
            should("return independent streams") {
                val op1 = processWrapper.getOutput()
                val reader1 = BufferedReader(InputStreamReader(op1))
                val op2 = processWrapper.getOutput()
                val reader2 = BufferedReader(InputStreamReader(op2))

                outputStream.write("hello\n".toByteArray())
                // Both output streams should see 'hello'
                reader1.readLine() shouldBe "hello"
                reader2.readLine() shouldBe "hello"
            }
        }
        "isAlive" {
            should("return false if the process is dead") {
                whenever(process.isAlive).thenReturn(false)
                processWrapper.isAlive shouldBe false
            }
            should("return true if the process is alive") {
                whenever(process.isAlive).thenReturn(true)
                processWrapper.isAlive shouldBe true
            }
        }
        "exitValue" {
            "when the process has exited" {
                should("return its exit code") {
                    whenever(process.exitValue()).thenReturn(42)
                    processWrapper.exitValue() shouldBe 42
                }
            }
            "when the process has not exited" {
                should("throw IllegalThreadStateException") {
                    whenever(process.exitValue()).doThrow(IllegalThreadStateException())
                    shouldThrow<IllegalThreadStateException> {
                        processWrapper.exitValue()
                    }
                }
            }
        }
    }
}
