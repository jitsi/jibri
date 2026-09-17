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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.runTestApplication
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.service.impl.YOUTUBE_URL
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.status.OverallHealth
import org.jitsi.jibri.webhooks.v1.WebhookClient

class HttpApiTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val jibriManager: JibriManager = mockk()
    private val jibriStatusManager: JibriStatusManager = mockk()
    private val webhookClient: WebhookClient = mockk()

    private val api = HttpApi(jibriManager, jibriStatusManager, webhookClient)

    init {
        context("health") {
            context("when jibri isn't busy") {
                val expectedStatus =
                    JibriStatus(ComponentBusyStatus.IDLE, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
                val expectedHealth = JibriHealth(expectedStatus)

                every { jibriManager.currentEnvironmentContext } returns null
                every { jibriStatusManager.overallStatus } returns expectedStatus

                apiTest {
                    val response = client.get("/jibri/api/v1.0/health")

                    should("call JibriStatusManager#overallStatus") {
                        verify { jibriStatusManager.overallStatus }
                    }
                    should("call JibriManager#currentEnvironmentContext") {
                        verify { jibriManager.currentEnvironmentContext }
                    }
                    should("return a status of 200") {
                        response.status shouldBe HttpStatusCode.OK
                    }
                    should("return the right json body") {
                        // The json should not include the 'environmentContext' field at all, since it
                        // will be null
                        response.bodyAsText() shouldNotContain "environmentContext"
                        val health = jacksonObjectMapper().readValue(response.bodyAsText(), JibriHealth::class.java)
                        health shouldBe expectedHealth
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
                    val response = client.get("/jibri/api/v1.0/health")

                    should("return a status of 200") {
                        response.status shouldBe HttpStatusCode.OK
                    }
                    should("return the right json body") {
                        response.bodyAsText() shouldContain "environmentContext"
                        val health = jacksonObjectMapper().readValue(response.bodyAsText(), JibriHealth::class.java)
                        health shouldBe expectedHealth
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
                    client.post("/jibri/api/v1.0/startService") {
                        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(json)
                    }

                    should("call JibriManager#startFileRecording with the right params") {
                        capturedServiceParams.captured.usageTimeoutMinutes shouldBe 0
                    }
                }
            }
            context("start streaming with a bare YouTube stream key") {
                val capturedStreamingParams = slot<StreamingParams>()
                every {
                    jibriManager.startStreaming(any(), capture(capturedStreamingParams), any(), any())
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
                    sinkType = RecordingSinkType.STREAM,
                    youTubeStreamKey = "abcd-1234-efgh-5678"
                )
                val json = jacksonObjectMapper().writeValueAsString(startServiceRequest)
                apiTest {
                    client.post("/jibri/api/v1.0/startService") {
                        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(json)
                    }

                    // The field is named for a bare key, and has always accepted one. Normalize it into a full
                    // RTMP URL the same way the XMPP API does, so it goes through validation instead of failing
                    // later with a confusing "no scheme" error.
                    should("normalize the bare key into a full YouTube RTMP URL") {
                        capturedStreamingParams.captured.rtmpUrl shouldBe "$YOUTUBE_URL/abcd-1234-efgh-5678"
                    }
                }
            }
            context("start streaming with a full RTMP URL") {
                val capturedStreamingParams = slot<StreamingParams>()
                every {
                    jibriManager.startStreaming(any(), capture(capturedStreamingParams), any(), any())
                } just Runs
                val fullUrl = "rtmp://example.com/live/abcd-1234-efgh-5678"
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
                    sinkType = RecordingSinkType.STREAM,
                    youTubeStreamKey = fullUrl
                )
                val json = jacksonObjectMapper().writeValueAsString(startServiceRequest)
                apiTest {
                    client.post("/jibri/api/v1.0/startService") {
                        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(json)
                    }

                    should("pass it through unchanged, not double-prefixed") {
                        capturedStreamingParams.captured.rtmpUrl shouldBe fullUrl
                    }
                }
            }
        }
    }
    private suspend fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        runTestApplication {
            application {
                with(api) {
                    apiModule()
                }
            }
            block()
        }
    }
}
