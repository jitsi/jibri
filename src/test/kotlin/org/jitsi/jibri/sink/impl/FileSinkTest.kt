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

package org.jitsi.jibri.sink.impl

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.random.Random

internal class FileSinkTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val fs = MemoryFileSystemBuilder.newLinux().build()

    init {
        context("when created") {
            val sink = FileSink(fs.getPath("/tmp/xxx"), "callname", "ext")
            should("have the correct path") {
                sink.path.shouldStartWith("/tmp/xxx")
                sink.path.shouldContain("callname")
                sink.path.shouldContain("ext")
            }
            should("have the correct format") {
                sink.format shouldBe "ext"
            }
        }
        context("when created with a really long call name") {
            val reallyLongCallName = String.randomAlphas(200)
            val sink = FileSink(fs.getPath("/tmp/xxx"), reallyLongCallName, "ext")
            should("not generate a filename longer than the max file length") {
                sink.file.fileName.toString().length shouldBe FileSink.MAX_FILENAME_LENGTH
            }
        }
    }

    // Generates a random string of lower-case a-z letters with the given size
    private fun String.Companion.randomAlphas(size: Int): String {
        val chars: List<Char> = ('a'..'z').toList()
        return (1..size)
            .map { Random.nextInt(0, chars.size) }
            .map(chars::get)
            .joinToString("")
    }
}
