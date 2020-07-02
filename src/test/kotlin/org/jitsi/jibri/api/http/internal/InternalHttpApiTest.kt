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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.jitsi.jibri.helpers.resetScheduledPool
import org.jitsi.jibri.helpers.setScheduledPool
import org.jitsi.jibri.util.TaskPools
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class InternalHttpApiTest : ShouldSpec() {
    private val executor: ScheduledExecutorService = mockk()
    private val future: ScheduledFuture<*> = mockk()

    init {
        beforeSpec {
            TaskPools.setScheduledPool(executor)
        }

        beforeTest {
            clearMocks(executor, future)
            every { executor.schedule(any(), any(), any()) } returns future
        }

        afterSpec {
            TaskPools.resetScheduledPool()
        }

        context("gracefulShutdown") {
            should("return a 200 and not invoke the shutdown handler directly") {
                var gracefulShutdownHandlerCalled = false
                val gracefulShutdownHandler = {
                    gracefulShutdownHandlerCalled = true
                }
                val internalHttpApi = InternalHttpApi({}, gracefulShutdownHandler, {})
                val response = internalHttpApi.gracefulShutdown()
                response.status shouldBe 200
                gracefulShutdownHandlerCalled shouldBe false
            }
        }

        context("notifyConfigChanged") {
            should("return a 200 and not invoke the config changed handler directly") {
                var configChangedHandlerCalled = false
                val configChangedHandler = {
                    configChangedHandlerCalled = true
                }
                val internalHttpApi = InternalHttpApi({}, configChangedHandler, {})
                val response = internalHttpApi.reloadConfig()
                response.status shouldBe 200
                configChangedHandlerCalled shouldBe false
            }
        }

        context("shutdown") {
            should("return a 200 and not invoke the shutdown handler directly") {
                var shutdownHandlerCalled = false
                val shutdownHandler = {
                    shutdownHandlerCalled = true
                }
                val internalHttpApi = InternalHttpApi({}, {}, shutdownHandler)
                val response = internalHttpApi.shutdown()
                response.status shouldBe 200
                shutdownHandlerCalled shouldBe false
            }
        }
    }
}
