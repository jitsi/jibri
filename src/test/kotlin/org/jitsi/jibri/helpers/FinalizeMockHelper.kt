/*
 * Copyright @ 2021 - present 8x8, Inc.
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

package org.jitsi.jibri.helpers

import io.mockk.every
import io.mockk.mockk
import org.jitsi.jibri.util.ProcessWrapper
import java.io.PipedInputStream
import java.io.PipedOutputStream

fun createFinalizeProcessMock(shouldSucceed: Boolean): ProcessWrapper {
    val op = PipedOutputStream()
    val stdOut = PipedInputStream(op)
    return mockk {
        every { getOutput() } returns stdOut
        every { waitFor() } answers {
            if (shouldSucceed) {
                0
            } else {
                1
            }
        }
        every { exitValue } answers {
            if (shouldSucceed) {
                0
            } else {
                1
            }
        }

        every { start() } answers {
            // Finish instantly and close the output stream so the task waiting on it to finish logging
            // doesn't have to block for long.
            op.close()
        }
    }
}
