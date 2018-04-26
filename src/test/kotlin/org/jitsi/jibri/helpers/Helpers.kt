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
package org.jitsi.jibri.helpers

import java.time.Duration

/**
 * Custom version of kotlin.test's [io.kotlintest.eventually] which uses milliseconds
 * and adds a wait between checks (and gives me much more consistent results than
 * kotlin.test's version)
 */
fun <T> eventually(duration: Duration, func: () -> T): T {
    val end = System.currentTimeMillis() + duration.toMillis()
    var times = 0
    while (System.currentTimeMillis() < end) {
        try {
            return func()
        } catch (e: Throwable) {
            if (!AssertionError::class.java.isAssignableFrom(e.javaClass)) {
                // Not the kind of exception we were prepared to tolerate
                throw e
            }
            // else ignore and continue
        }
        times++
        Thread.sleep(500)
    }
    throw AssertionError("Test failed after ${duration.seconds} seconds; attempted $times times")
}

/**
 * Ensures that, for the given [Duration], [func] should always evaluate
 * correctly
 */
fun <T> always(duration: Duration, func: () -> T) {
    val start = System.currentTimeMillis()
    val end = start + duration.toMillis()
    var times = 1
    while (System.currentTimeMillis() < end) {
        try {
            func()
        } catch (e: Throwable) {
            if (AssertionError::class.java.isAssignableFrom(e.javaClass)) {
                throw AssertionError("Test failed after ${System.currentTimeMillis() - start}ms; attempted $times times")
            }
        }
        times++
        Thread.sleep(500)
    }
}
