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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.helpers.within
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.logging.Logger
import kotlin.concurrent.thread

internal class LoggingUtilsKtTest : FunSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val process: ProcessWrapper = mockk()
    private lateinit var pipedOutputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private val logger: Logger = mockk(relaxed = true)

    init {
        beforeTest {
            pipedOutputStream = PipedOutputStream()
            inputStream = PipedInputStream(pipedOutputStream)
            clearMocks(logger)
            every { process.getOutput() } returns inputStream
        }

        test("logStream should write log lines to the given logger") {
            LoggingUtils.logOutputOfProcess(process, logger)
            thread {
                for (i in 0..4) {
                    pipedOutputStream.write("$i\n".toByteArray())
                }
            }

            val logLines = mutableListOf<String>()
            within(5.seconds) {
                verify(exactly = 5) { logger.info(capture(logLines)) }
            }
            logLines.forEachIndexed { index, value ->
                index.toString() shouldBe value
            }
        }

        test("logStream should complete the task when EOF is reached") {
            val streamClosed = LoggingUtils.logOutputOfProcess(process, logger)
            thread {
                for (i in 0..4) {
                    pipedOutputStream.write("$i\n".toByteArray())
                }
                pipedOutputStream.close()
            }
            val logLines = mutableListOf<String>()
            within(5.seconds) {
                verify(exactly = 5) { logger.info(capture(logLines)) }
            }
            logLines.forEachIndexed { index, value ->
                index.toString() shouldBe value
            }
            within(5.seconds) {
                streamClosed.isDone shouldBe true
            }
            streamClosed.get() shouldBe true
        }
    }
}
