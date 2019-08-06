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
 *
 */

package org.jitsi.jibri.api.xmpp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.IsolationMode
import io.kotlintest.TestCase
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.config.XmppMuc
import org.jitsi.jibri.service.AppData
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.status.OverallHealth
import org.jitsi.jibri.util.TaskPools
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.XMPPError
import org.jxmpp.jid.impl.JidCreate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class XmppApiTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private fun createJibriIq(iqAction: JibriIq.Action, iqMode: JibriIq.RecordingMode): JibriIq {
        return JibriIq().apply {
            recordingMode = iqMode
            action = iqAction
            // Note that the domain used below must match the ones in the xmpp env config
            from = JidCreate.from("from_jid@xmppDomain")
            room = JidCreate.entityBareFrom("room_jid@xmppDomain")
            sessionId = "session_id"
        }
    }

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        val executorService: ExecutorService = mock()
        whenever(executorService.submit(any<Runnable>())).then {
            (it.arguments.first() as Runnable).run()
            CompletableFuture<Unit>()
        }
        TaskPools.ioPool = executorService
    }

    init {
        val jibriManager: JibriManager = mock()
        val xmppConfig = XmppEnvironmentConfig(
            name = "xmppEnvName",
            xmppServerHosts = listOf("xmppServerHost1", "xmppServerHost2"),
            xmppDomain = "xmppDomain",
            controlLogin = XmppCredentials(
                domain = "controlXmppDomain",
                username = "xmppUsername",
                password = "xmppPassword"
            ),
            controlMuc = XmppMuc(
                domain = "xmppMucDomain",
                roomName = "xmppMucRoomName",
                nickname = "xmppMucNickname"
            ),
            sipControlMuc = XmppMuc(
                domain = "xmppSipMucDomain",
                roomName = "xmppSipMucRoomName",
                nickname = "xmppSipMucNickname"
            ),
            callLogin = XmppCredentials(
                domain = "callXmppDomain",
                username = "xmppCallUsername",
                password = "xmppCallPassword"
            ),
            stripFromRoomDomain = "",
            usageTimeoutMins = 0,
            trustAllXmppCerts = true
        )
        val jibriStatusManager: JibriStatusManager = mock()
        // the initial status is idle
        val expectedStatus =
                JibriStatus(ComponentBusyStatus.IDLE, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
        whenever(jibriStatusManager.overallStatus).thenReturn(expectedStatus)

        "xmppApi" {
            val xmppApi = XmppApi(jibriManager, listOf(xmppConfig), jibriStatusManager)
            val mucClientManager: MucClientManager = mock()
            // A dummy MucClient we'll use to be the one incoming messages are received on
            val mucClient: MucClient = mock()
            whenever(mucClient.id).thenReturn(xmppConfig.xmppServerHosts.first())
            xmppApi.start(mucClientManager)
            should("add itself as the IQ listener for the MUC client manager") {
                verify(mucClientManager).setIQListener(xmppApi)
            }
            should("create a muc client for each xmpp host") {
                verify(mucClientManager, times(2)).addMucClient(any())
            }

            "when receiving a start recording iq" {
                val jibriIq = createJibriIq(JibriIq.Action.START, JibriIq.RecordingMode.FILE)
                "and jibri is idle" {
                    val statusHandler = argumentCaptor<JibriServiceStatusHandler>()
                    whenever(jibriManager.startFileRecording(any(), any(), any(), statusHandler.capture())).thenAnswer { }
                    val response = xmppApi.handleIq(jibriIq, mucClient)
                    should("send a pending response to the original IQ request") {
                        response shouldNotBe null
                        response should beInstanceOf<JibriIq>()
                        response as JibriIq
                        response.status shouldBe JibriIq.Status.PENDING
                    }
                    "after the service starts up" {
                        statusHandler.firstValue(ComponentState.Running)
                        should("send a success response") {
                            val sentStanzas = argumentCaptor<Stanza>()
                            verify(mucClient).sendStanza(sentStanzas.capture())
                            sentStanzas.allValues.size shouldBe 1
                            (sentStanzas.firstValue as JibriIq).status shouldBe JibriIq.Status.ON
                        }
                    }
                }
                "with application data" {
                    val fileMetaData = mapOf<Any, Any>(
                        "file_recording_metadata" to mapOf<Any, Any>(
                            "upload_credentials" to mapOf<Any, Any>(
                                "service_name" to "file_service",
                                "token" to "file_service_token"
                            )
                        )
                    )
                    val appData = AppData(fileRecordingMetadata = fileMetaData)
                    val jsonString = jacksonObjectMapper().writeValueAsString(appData)
                    jibriIq.appData = jsonString

                    val serviceParams = argumentCaptor<ServiceParams>()
                    whenever(jibriManager.startFileRecording(serviceParams.capture(), any(), any(), any())).thenAnswer { }
                    xmppApi.handleIq(jibriIq, mucClient)
                    should("parse and pass the app data") {
                        serviceParams.allValues.size shouldBe 1
                        serviceParams.firstValue.appData shouldBe appData
                    }
                }
                "from a muc client it doesn't recognize" {
                    val unknownMucClient: MucClient = mock()
                    whenever(unknownMucClient.id).thenReturn("unknown name")
                    val result = xmppApi.handleIq(jibriIq, unknownMucClient)
                    result.error shouldNotBe null
                    result.error.condition shouldBe XMPPError.Condition.bad_request
                }
            }
        }
    }
}
