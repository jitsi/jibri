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

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.helpers.within
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit

internal class ProcessWrapperTest : ShouldSpec() {
    private val processBuilder: ProcessBuilder = mock()
    private val process: Process = mock()
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var processWrapper: ProcessWrapper

    override fun beforeTest(description: Description) {
        super.beforeTest(description)

        outputStream = PipedOutputStream()
        inputStream = PipedInputStream(outputStream)

        whenever(process.inputStream).thenReturn(inputStream)
        whenever(process.destroyForcibly()).thenReturn(process)
        whenever(processBuilder.start()).thenReturn(process)

        processWrapper = ProcessWrapper(listOf(), processBuilder = processBuilder)
        processWrapper.start()
    }

    init {
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
        "getMostRecentLine" {
            should("return empty string at first") {
                processWrapper.getMostRecentLine() shouldBe ""
            }
            should("be equal to the process stdout") {
                outputStream.write("hello\n".toByteArray())
                within(5.seconds()) {
                    processWrapper.getMostRecentLine() shouldBe "hello"
                }
            }
            should("update to the most recent line") {
                outputStream.write("hello\n".toByteArray())
                outputStream.write("goodbye\n".toByteArray())
                within(5.seconds()) {
                    processWrapper.getMostRecentLine() shouldBe "goodbye"
                }
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
                    processWrapper.exitValue shouldBe 42
                }
            }
            "when the process has not exited" {
                should("throw IllegalThreadStateException") {
                    whenever(process.exitValue()).doThrow(IllegalThreadStateException())
                    shouldThrow<IllegalThreadStateException> {
                        processWrapper.exitValue
                    }
                }
            }
        }
        "waitFor" {
            should("call the process' waitFor") {
                processWrapper.waitFor()
                verify(process).waitFor()
            }
        }
        "waitFor(timeout)" {
            should("call the process' waitFor(timeout')") {
                processWrapper.waitFor(10, TimeUnit.SECONDS)
                verify(process).waitFor(10, TimeUnit.SECONDS)
            }
        }
        "destroyForcibly" {
            should("call the process' destroyForcibly") {
                processWrapper.destroyForcibly()
                verify(process).destroyForcibly()
            }
        }
    }
}
