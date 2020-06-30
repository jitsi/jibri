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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.helpers.within
import org.mockito.ArgumentMatchers.anyString
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class ProcessWrapperTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val processBuilder: ProcessBuilder = mock()
    private val process: Process = mock()
    private val runtime: Runtime = mock()
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var processWrapper: ProcessWrapper

    init {
        beforeTest {
            outputStream = PipedOutputStream()
            inputStream = PipedInputStream(outputStream)

            whenever(process.inputStream).thenReturn(inputStream)
            whenever(process.destroyForcibly()).thenReturn(process)
            whenever(processBuilder.start()).thenReturn(process)

            processWrapper = ProcessWrapper(listOf(), processBuilder = processBuilder, runtime = runtime)
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
                within(5.seconds()) {
                    processWrapper.getMostRecentLine() shouldBe "hello"
                }
            }
            should("update to the most recent line") {
                outputStream.write("hello\n".toByteArray())
                outputStream.write("goodbye\n".toByteArray())
                within(5.seconds()) {
                    processWrapper.getMostRecentLine() shouldBe "goodbye"
                }
            }
        }
        context("isAlive") {
            should("return false if the process is dead") {
                whenever(process.isAlive).thenReturn(false)
                processWrapper.isAlive shouldBe false
            }
            should("return true if the process is alive") {
                whenever(process.isAlive).thenReturn(true)
                processWrapper.isAlive shouldBe true
            }
        }
        context("exitValue") {
            context("when the process has exited") {
                should("return its exit code") {
                    whenever(process.exitValue()).thenReturn(42)
                    processWrapper.exitValue shouldBe 42
                }
            }
            context("when the process has not exited") {
                should("throw IllegalThreadStateException") {
                    whenever(process.exitValue()).doThrow(IllegalThreadStateException())
                    shouldThrow<IllegalThreadStateException> {
                        processWrapper.exitValue
                    }
                }
            }
        }
        context("waitFor") {
            should("call the process' waitFor") {
                processWrapper.waitFor()
                verify(process).waitFor()
            }
        }
        context("waitFor(timeout)") {
            should("call the process' waitFor(timeout')") {
                processWrapper.waitFor(10, TimeUnit.SECONDS)
                verify(process).waitFor(10, TimeUnit.SECONDS)
            }
        }
        context("destroyForcibly") {
            should("call the process' destroyForcibly") {
                processWrapper.destroyForcibly()
                verify(process).destroyForcibly()
            }
        }
        context("stop") {
            should("invoke the correct command") {
                val execCaptor = argumentCaptor<String>()
                whenever(runtime.exec(execCaptor.capture())).thenReturn(process)
                processWrapper.stop()
                execCaptor.firstValue.let {
                    it shouldContain "kill -s SIGINT"
                }
            }
            context("when the runtime throws IOException") {
                whenever(runtime.exec(anyString())).thenAnswer { throw IOException() }
                should("let the exception bubble up") {
                    shouldThrow<IOException> { processWrapper.stop() }
                }
            }
            context("when the runtime throws RuntimeException") {
                whenever(runtime.exec(anyString())).thenAnswer { throw RuntimeException() }
                should("let the exception bubble up") {
                    shouldThrow<java.lang.RuntimeException> { processWrapper.stop() }
                }
            }
        }
        context("stopAndWaitFor") {
            should("invoke the correct command") {
                val execCaptor = argumentCaptor<String>()
                whenever(runtime.exec(execCaptor.capture())).thenReturn(process)
                whenever(process.waitFor(any(), any())).thenReturn(true)
                processWrapper.stopAndWaitFor(Duration.ofSeconds(10)) shouldBe true
                execCaptor.firstValue.let {
                    it shouldContain "kill -s SIGINT"
                }
            }
            context("when the runtime throws IOException") {
                whenever(runtime.exec(anyString())).thenAnswer { throw IOException() }
                should("handle it correctly") {
                    processWrapper.stopAndWaitFor(Duration.ofSeconds(10)) shouldBe false
                }
            }
            context("when the runtime throws RuntimeException") {
                whenever(runtime.exec(anyString())).thenAnswer { throw RuntimeException() }
                should("handle it correctly") {
                    processWrapper.stopAndWaitFor(Duration.ofSeconds(10)) shouldBe false
                }
            }
        }
        context("destroyForciblyAndWaitFor") {
            should("invoke the correct command") {
                whenever(process.waitFor(any(), any())).thenReturn(true)
                processWrapper.destroyForciblyAndWaitFor(Duration.ofSeconds(10)) shouldBe true
                verify(process).destroyForcibly()
            }
            context("when waitFor returns false") {
                whenever(process.waitFor(any(), any())).thenReturn(false)
                processWrapper.destroyForciblyAndWaitFor(Duration.ofSeconds(10)) shouldBe false
            }
            context("when waitFor throws") {
                whenever(process.waitFor(any(), any())).thenAnswer { throw InterruptedException() }
                processWrapper.destroyForciblyAndWaitFor(Duration.ofSeconds(10)) shouldBe false
            }
        }
    }
}
