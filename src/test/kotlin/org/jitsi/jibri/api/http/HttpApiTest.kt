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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.IsolationMode
import io.kotlintest.Spec
import io.kotlintest.matchers.string.contain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNot
import io.kotlintest.specs.ShouldSpec
import org.glassfish.jersey.jackson.JacksonFeature
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
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
import javax.ws.rs.client.Entity
import javax.ws.rs.core.Application
import javax.ws.rs.ext.ContextResolver

class HttpApiTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val jibriManager: JibriManager = mock()
    private val jibriStatusManager: JibriStatusManager = mock()
    private lateinit var jerseyTest: JerseyTest

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        jerseyTest = object : JerseyTest() {
            override fun configure(): Application {
                return ResourceConfig(object : ResourceConfig() {
                    init {
                        // Uncommenting the following line can help with debugging any errors
                        // property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_SERVER, "WARNING")
                        register(ContextResolver<ObjectMapper> { ObjectMapper().registerKotlinModule() })
                        register(JacksonFeature::class.java)
                        registerInstances(HttpApi(jibriManager, jibriStatusManager))
                    }
                })
            }
        }
        jerseyTest.setUp()
    }

    override fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        jerseyTest.tearDown()
    }

    init {
        "health" {
            "when jibri isn't busy" {
                val expectedStatus =
                        JibriStatus(ComponentBusyStatus.IDLE, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
                val expectedHealth = JibriHealth(expectedStatus)

                whenever(jibriManager.currentEnvironmentContext)
                    .thenReturn(null)
                whenever(jibriStatusManager.overallStatus).thenReturn(expectedStatus)

                val res = jerseyTest.target("/jibri/api/v1.0/health").request()
                    .get()
                should("call JibriStatusManager#overallStatus") {
                    verify(jibriStatusManager).overallStatus
                }
                should("call JibriManager#currentEnvironmentContext") {
                    verify(jibriManager).currentEnvironmentContext
                }
                should("return a status of 200") {
                    res.status shouldBe 200
                }
                should("return the right json body") {
                    val json = res.readEntity(String::class.java)
                    // The json should not include the 'environmentContext' field at all, since it
                    // will be null
                    json shouldNot contain("environmentContext")
                    val health = jacksonObjectMapper().readValue(json, JibriHealth::class.java)
                    health shouldBe expectedHealth
                }
            }
            "when jibri is busy and has an environmentContext" {
                val expectedStatus =
                        JibriStatus(ComponentBusyStatus.BUSY, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
                val expectedEnvironmentContext = EnvironmentContext("meet.jit.si")
                val expectedHealth = JibriHealth(expectedStatus, expectedEnvironmentContext)

                whenever(jibriManager.currentEnvironmentContext).thenReturn(expectedEnvironmentContext)
                whenever(jibriStatusManager.overallStatus).thenReturn(expectedStatus)

                val res = jerseyTest.target("/jibri/api/v1.0/health").request()
                    .get()
                should("return a status of 200") {
                    res.status shouldBe 200
                }
                should("return the right json body") {
                    val json = res.readEntity(String::class.java)
                    // The json should not include the 'environmentContext' field at all, since it
                    // will be null
                    val health = jacksonObjectMapper().readValue(json, JibriHealth::class.java)
                    health shouldBe expectedHealth
                }
            }
        }
        "startService" {
            "start file recording" {
                val capturedServiceParams = argumentCaptor<ServiceParams>()
                whenever(jibriManager.startFileRecording(
                    capturedServiceParams.capture(),
                    any(),
                    anyOrNull(),
                    anyOrNull())
                ).thenAnswer { }
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
                val res = jerseyTest
                    .target("/jibri/api/v1.0/startService")
                    .request()
                    .post(Entity.json(json))
                should("return a 200") {
                    res.status shouldBe 200
                }
                should("call JibriManager#startFileRecording with the right params") {
                    capturedServiceParams.firstValue.usageTimeoutMinutes shouldBe 0
                }
            }
        }
    }
}
