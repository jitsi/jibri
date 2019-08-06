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

import io.kotlintest.IsolationMode
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.helpers.seconds
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream

internal class TeeLogicTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)
    private val tee = TeeLogic(inputStream)

    private fun pushIncomingData(data: Byte) {
        outputStream.write(data.toInt())
        tee.read()
    }

    private fun pushIncomingData(data: String) {
        data.toByteArray().forEach {
            pushIncomingData(it)
        }
    }

    init {
        "data written" {
            "after the creation of a branch" {
                should("be received by that branch") {
                    val branch = tee.addBranch()
                    pushIncomingData("hello, world\n")
                    val reader = BufferedReader(InputStreamReader(branch))
                    reader.readLine() shouldBe "hello, world"
                }
            }
            "before the creation of a branch" {
                should("not be received by that branch") {
                    pushIncomingData("hello, world\n")
                    val branch = tee.addBranch()
                    pushIncomingData("goodbye, world\n")
                    val reader = BufferedReader(InputStreamReader(branch))
                    reader.readLine() shouldBe "goodbye, world"
                }
            }
        }
        "end of stream" {
            should("throw EndOfStreamException") {
                outputStream.close()
                shouldThrow<EndOfStreamException> {
                    tee.read()
                }
            }
            should("close all branches") {
                val branch1 = tee.addBranch()
                val branch2 = tee.addBranch()
                outputStream.close()
                shouldThrow<EndOfStreamException> {
                    tee.read()
                }
                val reader1 = BufferedReader(InputStreamReader(branch1))
                reader1.readLine() shouldBe null

                val reader2 = BufferedReader(InputStreamReader(branch2))
                reader2.readLine() shouldBe null
            }
        }
    }
}

internal class TeeTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true

    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)
    private val tee = Tee(inputStream)

    init {
        val branch = tee.addBranch()
        "stop" {
            should("close downstream readers").config(timeout = 5.seconds()) {
                tee.stop()
                // This is testing that it returns the proper value (to signal
                // the stream was closed).  But the timeout in the config tests
                // is also critical because if the stream wasn't closed, then
                // the read call will block indefinitely
                branch.read() shouldBe -1
            }
        }
    }
}
