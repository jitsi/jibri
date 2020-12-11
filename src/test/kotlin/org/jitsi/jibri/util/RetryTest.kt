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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlin.time.milliseconds

class RetryTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    context("retryWithBackoff") {
        should("retry 5 times by default if the task fails (and throw the final exception)") {
            val task = ThrowingTask()
            runBlockingTest {
                launch {
                    shouldThrow<Exception> {
                        retryWithBackoff {
                            task.run()
                        }
                    }
                }
                advanceUntilIdle()
            }
            task.numTimesCalled shouldBe 5
        }

        should("double the delay time between each attempt") {
            val task = ThrowingTask()
            runBlockingTest {
                launch {
                    shouldThrow<Exception> {
                        retryWithBackoff {
                            task.run()
                        }
                    }
                }
                task.numTimesCalled shouldBe 1
                advanceTimeBy(500)
                task.numTimesCalled shouldBe 2
                advanceTimeBy(1_000)
                task.numTimesCalled shouldBe 3
                advanceTimeBy(2_000)
                task.numTimesCalled shouldBe 4
                advanceTimeBy(4_000)
                task.numTimesCalled shouldBe 5
                advanceUntilIdle()
                task.numTimesCalled shouldBe 5
            }
        }

        should("only retry if the task throws") {
            val task = ThrowingTask(numFailures = 3)
            runBlockingTest {
                launch {
                    val result = retryWithBackoff {
                        task.run()
                    }
                    result shouldBe 42
                }
                advanceUntilIdle()
                task.numTimesCalled shouldBe 4
            }
        }

        should("respect a given number of retry attempts") {
            val task = ThrowingTask()
            runBlockingTest {
                launch {
                    shouldThrow<Exception> {
                        retryWithBackoff(numAttempts = 3) {
                            task.run()
                        }
                    }
                }
                advanceUntilIdle()
            }
            task.numTimesCalled shouldBe 3
        }

        should("respect a given initial delay") {
            val task = ThrowingTask()
            runBlockingTest {
                launch {
                    shouldThrow<Exception> {
                        retryWithBackoff(initialDelay = 1.milliseconds) {
                            task.run()
                        }
                    }
                }
                task.numTimesCalled shouldBe 1
                advanceTimeBy(1)
                task.numTimesCalled shouldBe 2
                advanceTimeBy(2)
                task.numTimesCalled shouldBe 3
                advanceTimeBy(4)
                task.numTimesCalled shouldBe 4
                advanceTimeBy(8)
                task.numTimesCalled shouldBe 5
                advanceUntilIdle()
                task.numTimesCalled shouldBe 5
            }
        }
    }
})

private class ThrowingTask(
    private val numFailures: Int = Int.MAX_VALUE
) {
    var numTimesCalled = 0
        private set

    fun run(): Int {
        numTimesCalled++
        if (numTimesCalled <= numFailures) {
            throw Exception("boom")
        }
        return 42
    }
}
