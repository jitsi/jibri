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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.helpers.within
import org.jitsi.utils.logging2.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

@Suppress("BlockingMethodInNonBlockingContext")
internal class ProcessWrapperTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val process: Process = mockk(relaxed = true)
    private var stoppedPid: Long = -1
    private lateinit var stopperException: Throwable
    private var stopperThrows = false
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var processWrapper: ProcessWrapper
    private val logger: Logger = mockk(relaxed = true)

    init {
        beforeTest {
            outputStream = PipedOutputStream()
            inputStream = PipedInputStream(outputStream)
            stopperThrows = false

            every { process.inputStream } returns inputStream
            every { process.destroyForcibly() } returns process
            every { process.pid() } returns 12345L

            val stopper: ProcessStopper = { pid ->
                if (stopperThrows) throw stopperException
                stoppedPid = pid
            }

            processWrapper = ProcessWrapper(
                listOf(),
                parentLogger = logger,
                processStarter = { process },
                stopper = stopper
            )
            processWrapper.start()
        }
        context("getOutput") {
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
        context("getMostRecentLine") {
            should("return empty string at first") {
                processWrapper.getMostRecentLine() shouldBe ""
            }
            should("be equal to the process stdout") {
                outputStream.write("hello\n".toByteArray())
                within(5.seconds) {
                    processWrapper.getMostRecentLine() shouldBe "hello"
                }
            }
            should("update to the most recent line") {
                outputStream.write("hello\n".toByteArray())
                outputStream.write("goodbye\n".toByteArray())
                within(5.seconds) {
                    processWrapper.getMostRecentLine() shouldBe "goodbye"
                }
            }
        }
        context("isAlive") {
            should("return false if the process is dead") {
                every { process.isAlive } returns false
                processWrapper.isAlive shouldBe false
            }
            should("return true if the process is alive") {
                every { process.isAlive } returns true
                processWrapper.isAlive shouldBe true
            }
        }
        context("exitValue") {
            context("when the process has exited") {
                should("return its exit code") {
                    every { process.exitValue() } returns 42
                    processWrapper.exitValue shouldBe 42
                }
            }
            context("when the process has not exited") {
                should("throw IllegalThreadStateException") {
                    every { process.exitValue() } throws IllegalThreadStateException()
                    shouldThrow<IllegalThreadStateException> {
                        processWrapper.exitValue
                    }
                }
            }
        }
        context("waitFor") {
            should("call the process' waitFor") {
                processWrapper.waitFor()
                verify { process.waitFor() }
            }
        }
        context("waitFor(timeout)") {
            should("call the process' waitFor(timeout')") {
                processWrapper.waitFor(10, TimeUnit.SECONDS)
                verify { process.waitFor(10, TimeUnit.SECONDS) }
            }
        }
        context("destroyForcibly") {
            should("call the process' destroyForcibly") {
                processWrapper.destroyForcibly()
                verify { process.destroyForcibly() }
            }
        }
        context("stop") {
            should("invoke the stopper with the process pid") {
                processWrapper.stop()
                stoppedPid shouldBe 12345L
            }
            context("when the stopper throws IOException") {
                should("let the exception bubble up") {
                    stopperThrows = true
                    stopperException = java.io.IOException()
                    shouldThrow<java.io.IOException> { processWrapper.stop() }
                }
            }
            context("when the stopper throws RuntimeException") {
                should("let the exception bubble up") {
                    stopperThrows = true
                    stopperException = RuntimeException()
                    shouldThrow<RuntimeException> { processWrapper.stop() }
                }
            }
        }
        context("stopAndWaitFor") {
            should("invoke the stopper and wait") {
                every { process.waitFor(any(), any()) } returns true
                processWrapper.stopAndWaitFor(Duration.ofSeconds(10)) shouldBe true
                stoppedPid shouldBe 12345L
            }
            context("when the stopper throws IOException") {
                should("handle it correctly") {
                    stopperThrows = true
                    stopperException = java.io.IOException()
                    processWrapper.stopAndWaitFor(Duration.ofSeconds(10)) shouldBe false
                }
            }
            context("when the stopper throws RuntimeException") {
                should("handle it correctly") {
                    stopperThrows = true
                    stopperException = RuntimeException()
                    processWrapper.stopAndWaitFor(Duration.ofSeconds(10)) shouldBe false
                }
            }
        }
        context("destroyForciblyAndWaitFor") {
            should("invoke the correct command") {
                every { process.waitFor(any(), any()) } returns true
                processWrapper.destroyForciblyAndWaitFor(Duration.ofSeconds(10)) shouldBe true
                verify { process.destroyForcibly() }
            }
            context("when waitFor returns false") {
                every { process.waitFor(any(), any()) } returns false
                processWrapper.destroyForciblyAndWaitFor(Duration.ofSeconds(10)) shouldBe false
            }
            context("when waitFor throws") {
                every { process.waitFor(any(), any()) } throws InterruptedException()
                processWrapper.destroyForciblyAndWaitFor(Duration.ofSeconds(10)) shouldBe false
            }
        }
    }
}
