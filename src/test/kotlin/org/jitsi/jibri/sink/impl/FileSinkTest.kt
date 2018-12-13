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
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

internal class FileSinkTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true
    private val fs = MemoryFileSystemBuilder.newLinux().build()

    init {
        "when created" {
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
    }
}
