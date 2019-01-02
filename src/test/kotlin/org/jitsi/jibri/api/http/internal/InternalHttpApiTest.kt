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

package org.jitsi.jibri.api.http.internal

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.jibri.util.TaskPools
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class InternalHttpApiTest : ShouldSpec() {
    private val executor: ScheduledExecutorService = mock()
    private val future: ScheduledFuture<*> = mock()

    override fun beforeTest(description: Description) {
        super.beforeTest(description)
        reset(executor, future)
        whenever(executor.schedule(any(), any(), any())).thenReturn(future)
        TaskPools.recurringTasksPool = executor
    }

    init {
        "gracefulShutdown" {
            should("return a 200 and not invoke the shutdown handler directly") {
                var gracefulShutdownHandlerCalled = false
                val gracefulShutdownHandler = {
                    gracefulShutdownHandlerCalled = true
                }
                val internalHttpApi = InternalHttpApi(gracefulShutdownHandler, {})
                val response = internalHttpApi.gracefulShutdown()
                response.status shouldBe 200
                gracefulShutdownHandlerCalled shouldBe false
            }
        }

        "shutdown" {
            should("return a 200 and not invoke the shutdown handler directly") {
                var shutdownHandlerCalled = false
                val shutdownHandler = {
                    shutdownHandlerCalled = true
                }
                val internalHttpApi = InternalHttpApi({}, shutdownHandler)
                val response = internalHttpApi.shutdown()
                response.status shouldBe 200
                shutdownHandlerCalled shouldBe false
            }
        }
    }
}
