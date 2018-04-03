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
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class InternalHttpApiTest {
    private val executor: ScheduledExecutorService = mock()
    private val future: ScheduledFuture<*> = mock()

    @BeforeMethod
    fun setUp() {
        reset(executor, future)
        whenever(executor.schedule(any(), any(), any())).thenReturn(future)
    }

    @Test
    fun testGracefulShutdown() {
        var gracefulShutdownHandlerCalled = false
        val gracefulShutdownHandler = {
            gracefulShutdownHandlerCalled = true
        }
        val internalHttpApi = InternalHttpApi(executor, gracefulShutdownHandler, {})

        val response = internalHttpApi.gracefulShutdown()
        assertEquals(response.status, 200)
        // This should not have been called directly, it should have been scheduled
        assertFalse(gracefulShutdownHandlerCalled)
    }

    @Test
    fun testShutdown() {
        var shutdownHandlerCalled = false
        val shutdownHandler = {
            shutdownHandlerCalled = true
        }
        val internalHttpApi = InternalHttpApi(executor, {}, shutdownHandler)

        val response = internalHttpApi.shutdown()
        assertEquals(response.status, 200)
        // This should not have been called directly, it should have been scheduled
        assertFalse(shutdownHandlerCalled)
    }
}
