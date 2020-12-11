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

package org.jitsi.jibri

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.jitsi.jibri.job.IntermediateJobState
import org.jitsi.jibri.job.Running
import org.jitsi.jibri.job.StartingUp

class JibriSessionImplTest : ShouldSpec({
    val deferred: Deferred<Unit> = mockk()
    val stateUpdates = MutableStateFlow<IntermediateJobState>(StartingUp)

    val session = JibriSessionImpl(deferred, stateUpdates)

    context("onRunning") {
        should("only invoke the block after the state has gone to running") {
            var ran = false
            runBlockingTest {
                launch {
                    session.onRunning {
                        ran = true
                    }
                }
                ran shouldBe false
                stateUpdates.value = Running
                ran shouldBe true
            }
        }
    }
})
