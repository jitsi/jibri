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

package org.jitsi.jibri.api.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContextScope
import io.kotest.core.test.TestContext
import io.kotest.core.test.TestName
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.status.OverallHealth

class HttpApiTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val jibriManager: JibriManager = mockk()
    private val jibriStatusManager: JibriStatusManager = mockk()

    private val api = HttpApi(jibriManager, jibriStatusManager)

    init {
        context("health") {
            context("when jibri isn't busy") {
                val expectedStatus =
                        JibriStatus(ComponentBusyStatus.IDLE, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
                val expectedHealth = JibriHealth(expectedStatus)

                every { jibriManager.currentEnvironmentContext } returns null
                every { jibriStatusManager.overallStatus } returns expectedStatus

                apiTest {
                    with(handleRequest(HttpMethod.Get, "/jibri/api/v1.0/health")) {
                        shouldb("call JibriStatusManager#overallStatus") {
                            verify { jibriStatusManager.overallStatus }
                        }
                        shouldb("call JibriManager#currentEnvironmentContext") {
                            verify { jibriManager.currentEnvironmentContext }
                        }
                        shouldb("return a status of 200") {
                            response.status() shouldBe HttpStatusCode.OK
                        }
                        shouldb("return the right json body") {
                            // The json should not include the 'environmentContext' field at all, since it
                            // will be null
                            response.content shouldNotContain "environmentContext"
                            val health = jacksonObjectMapper().readValue(response.content, JibriHealth::class.java)
                            health shouldBe expectedHealth
                        }
                    }
                }
            }
            context("when jibri is busy and has an environmentContext") {
                val expectedStatus =
                    JibriStatus(ComponentBusyStatus.BUSY, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
                val expectedEnvironmentContext = EnvironmentContext("meet.jit.si")
                val expectedHealth = JibriHealth(expectedStatus, expectedEnvironmentContext)

                every { jibriManager.currentEnvironmentContext } returns expectedEnvironmentContext
                every { jibriStatusManager.overallStatus } returns expectedStatus

                apiTest {
                    with(handleRequest(HttpMethod.Get, "/jibri/api/v1.0/health")) {
                        shouldb("return a status of 200") {
                            response.status() shouldBe HttpStatusCode.OK
                        }
                        shouldb("return the right json body") {
                            response.content shouldContain "environmentContext"
                            val health = jacksonObjectMapper().readValue(response.content, JibriHealth::class.java)
                            health shouldBe expectedHealth
                        }
                    }
                }
            }
        }
        context("startService") {
            context("start file recording") {
                val capturedServiceParams = slot<ServiceParams>()
                every {
                    jibriManager.startFileRecording(
                        capture(capturedServiceParams),
                        any(),
                        any(),
                        any()
                    )
                } just Runs
                val startServiceRequest = StartServiceParams(
                    sessionId = "session_id",
                    callParams = CallParams(
                        callUrlInfo = CallUrlInfo("https://meet.jit.si", "callName")
                    ),
                    callLoginParams = XmppCredentials(
                        domain = "xmpp_domain",
                        username = "xmpp_username",
                        password = "xmpp_password"
                    ),
                    sinkType = RecordingSinkType.FILE
                )
                val json = jacksonObjectMapper().writeValueAsString(startServiceRequest)
                apiTest {
                    handleRequest(HttpMethod.Post, "/jibri/api/v1.0/startService") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(json)
                    }.apply {
                        shouldb("call JibriManager#startFileRecording with the right params") {
                            capturedServiceParams.captured.usageTimeoutMinutes shouldBe 0
                        }
                    }
                }
            }
        }
    }
    private fun <R> apiTest(block: TestApplicationEngine.() -> R) {
        with(api) {
            io.ktor.server.testing.withTestApplication({
                apiModule()
            }) {
                block()
            }
        }
    }
}

/**
 * A non-suspend version of `should` so that it can be called from within the KTOR test harness
 * scope
 */
fun ShouldSpecContextScope.shouldb(name: String, test: suspend TestContext.() -> Unit) =
    runBlocking { addTest(TestName("should ", name), xdisabled = false, test = test) }
