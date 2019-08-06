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

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.Description
import io.kotlintest.IsolationMode
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.helpers.within
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.logging.Logger
import kotlin.concurrent.thread

internal class LoggingUtilsKtTest : FunSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val process: ProcessWrapper = mock()
    private lateinit var pipedOutputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private val logger: Logger = mock()

    override fun beforeTest(description: Description) {
        super.beforeTest(description)
        pipedOutputStream = PipedOutputStream()
        inputStream = PipedInputStream(pipedOutputStream)
        reset(logger)
        whenever(process.getOutput()).thenReturn(inputStream)
    }

    init {
        test("logStream should write log lines to the given logger") {
            LoggingUtils.logOutput(process, logger)
            thread {
                for (i in 0..4) {
                    pipedOutputStream.write("$i\n".toByteArray())
                }
            }

            val logLine = argumentCaptor<String>()
            within(5.seconds()) {
                verify(logger, times(5)).info(logLine.capture())
            }
            logLine.allValues.forEachIndexed { index, value ->
                index.toString() shouldBe value
            }
        }

        test("logStream should complete the task when EOF is reached") {
            val streamClosed = LoggingUtils.logOutput(process, logger)
            thread {
                for (i in 0..4) {
                    pipedOutputStream.write("$i\n".toByteArray())
                }
                pipedOutputStream.close()
            }
            val logLine = argumentCaptor<String>()
            within(5.seconds()) {
                verify(logger, times(5)).info(logLine.capture())
            }
            logLine.allValues.forEachIndexed { index, value ->
                index.toString() shouldBe value
            }
            within(5.seconds()) {
                streamClosed.isDone shouldBe true
            }
            streamClosed.get() shouldBe true
        }
    }
}
