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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.io.PipedInputStream
import java.io.PipedOutputStream

class TailLogicTest : ShouldSpec() {
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var tail: TailLogic

    private fun writeLine(data: String) {
        val line = if (data.endsWith("\n")) data else data + "\n"
        line.toByteArray().forEach {
            outputStream.write(it.toInt())
        }
    }

    init {
        beforeTest {
            outputStream = PipedOutputStream()
            inputStream = PipedInputStream(outputStream)
            tail = TailLogic(inputStream)
        }
        context("mostRecentLine") {
            context("initially") {
                should("equal an empty string") {
                    tail.mostRecentLine shouldBe ""
                }
            }
            context("after writing once") {
                should("equal that line") {
                    writeLine("hello, world")
                    tail.readLine()
                    tail.mostRecentLine shouldBe "hello, world"
                }
            }
            context("after writing multiple times") {
                should("equal the most recent line") {
                    writeLine("hello, world")
                    tail.readLine()
                    writeLine("goodbye, world")
                    tail.readLine()
                    tail.mostRecentLine shouldBe "goodbye, world"
                }
            }
        }
    }
}
