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

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.milliseconds

/**
 * Retry [block] up to [numAttempts] until it doesn't throw.  Delay between each retry, starting with
 * [initialDelay] and then doubling each time.  If [block] never succeeds, the exception it throws
 * on the last attempt will be thrown.
 */
suspend fun <T> retryWithBackoff(
    initialDelay: Duration = 500.milliseconds,
    numAttempts: Int = 5,
    block: suspend () -> T
): T {
    var delay = initialDelay
    repeat(numAttempts - 1) {
        try {
            return block()
        } catch (t: Throwable) {
            delay(delay)
            delay *= 2
        }
    }
    // We don't want to delay after the final attempt if it fails, so run it outside the
    // loop/try-catch
    return block()
}