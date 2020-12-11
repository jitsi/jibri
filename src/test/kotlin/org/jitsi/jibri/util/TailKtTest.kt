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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class TailKtTest : ShouldSpec({
    context("tail") {
        should("emit all lines of an inputstream") {
            val str = buildString {
                repeat(10) {
                    appendLine("This is line $it")
                }
            }.byteInputStream()

            val tailedOutput = tail(str)
            var iter = 0
            tailedOutput.collectUntilEof {
                it shouldBe "This is line ${iter++}"
            }
            iter shouldBe 10
        }

        should("only replay the set amount of elements") {
            val str = buildString {
                repeat(10) {
                    appendLine("This is line $it")
                }
            }.byteInputStream()
            val tailedOutput = tail(str, replay = 1)
            tailedOutput.collectUntilEof {
                it shouldBe "This is line 9"
            }
        }

        should("update all collectors") {
            val (input, output) = createInputOutputStreams()
            val tailedOutput = tail(input)
            val numLines = GlobalScope.async {
                var numLinesSeen = 0
                tailedOutput.collectUntilEof {
                    numLinesSeen++
                }
                numLinesSeen
            }
            val numLinesTwo = GlobalScope.async {
                var numLinesSeen = 0
                tailedOutput.collectUntilEof {
                    numLinesSeen++
                }
                numLinesSeen
            }
            repeat(10) {
                output.write("Hello $it\n".toByteArray())
            }
            output.close()
            numLines.await() + numLinesTwo.await() shouldBe 20
        }
    }
})

private fun createInputOutputStreams(): Pair<InputStream, OutputStream> {
    val o = PipedOutputStream()
    val i = PipedInputStream(o)

    return i to o
}

private suspend inline fun SharedFlow<String>.collectUntilEof(crossinline action: suspend (value: String) -> Unit) =
    takeWhile { it != EOF }.collect { action(it) }
