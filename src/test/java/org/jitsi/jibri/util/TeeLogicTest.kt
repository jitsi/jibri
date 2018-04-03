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

import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream

class TeeLogicTest : ShouldSpec() {
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var tee: TeeLogic

    private fun pushIncomingData(data: Byte) {
        outputStream.write(data.toInt())
        tee.read()
    }

    override fun beforeTest(description: Description) {
        super.beforeTest(description)
        outputStream = PipedOutputStream()
        inputStream = PipedInputStream(outputStream)
        tee = TeeLogic(inputStream)
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
    }
}
