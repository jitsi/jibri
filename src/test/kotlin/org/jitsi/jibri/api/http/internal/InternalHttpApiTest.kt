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

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication

class InternalHttpApiTest : FunSpec() {

    private var gracefulShutdownHandlerCalls = 0
    private var shutdownHandlerCalls = 0
    private var configChangedHandlerCalls = 0

    private val internalApi = InternalHttpApi(
        { configChangedHandlerCalls++ },
        { gracefulShutdownHandlerCalls++ },
        { shutdownHandlerCalls++ }
    )

    init {
        isolationMode = IsolationMode.InstancePerLeaf

        test("gracefulShutdown should return a 200 and invoke the graceful shutdown handler") {
            apiTest {
                with(handleRequest(HttpMethod.Post, "/jibri/api/internal/v1.0/gracefulShutdown")) {
                    response.status() shouldBe HttpStatusCode.OK
                    gracefulShutdownHandlerCalls shouldBe 1
                    configChangedHandlerCalls shouldBe 0
                    shutdownHandlerCalls shouldBe 0
                }
            }
        }

        test("notifyConfigChanged should return a 200 and invoke the config changed handler") {
            apiTest {
                with(handleRequest(HttpMethod.Post, "/jibri/api/internal/v1.0/notifyConfigChanged")) {
                    response.status() shouldBe HttpStatusCode.OK
                    gracefulShutdownHandlerCalls shouldBe 0
                    configChangedHandlerCalls shouldBe 1
                    shutdownHandlerCalls shouldBe 0
                }
            }
        }

        test("shutdown should return a 200 and invoke the shutdown handler") {
            apiTest {
                with(handleRequest(HttpMethod.Post, "/jibri/api/internal/v1.0/shutdown")) {
                    response.status() shouldBe HttpStatusCode.OK
                    gracefulShutdownHandlerCalls shouldBe 0
                    configChangedHandlerCalls shouldBe 0
                    shutdownHandlerCalls shouldBe 1
                }
            }
        }
    }
    private fun <R> apiTest(block: TestApplicationEngine.() -> R) {
        with(internalApi) {
            withTestApplication({
                internalApiModule()
            }) {
                block()
            }
        }
    }
}
