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

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

import java.io.BufferedReader
import java.io.InputStreamReader
import org.jitsi.jibri.util.extensions.pidValue

internal class ProcessPidValueTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        context("Check pid of child process") {
            should("verify that pid of child process is correct") {
                var process = ProcessBuilder("sh", "-c", "echo $$; sleep 1")
                    .redirectErrorStream(true).start()
                val pidFromPidValue = "${process.pidValue}"
                val pidFromStdout = BufferedReader(InputStreamReader(process.inputStream)).readLine()
                pidFromPidValue shouldBe pidFromStdout
            }
        }
    }
}
