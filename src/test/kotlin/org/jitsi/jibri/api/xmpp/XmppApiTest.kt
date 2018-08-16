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

package org.jitsi.jibri.api.xmpp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.StartServiceResult
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.config.XmppMuc
import org.jitsi.jibri.service.AppData
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.xmpp.mucclient.MucClient
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.impl.JidCreate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class XmppApiTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true

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

    init {
        val executorService: ExecutorService = mock()
        whenever(executorService.submit(any<Runnable>())).then {
            (it.arguments.first() as Runnable).run()
            CompletableFuture<Unit>()
        }
        val jibriManager: JibriManager = mock()
        val xmppConfig = XmppEnvironmentConfig(
            name = "xmppEnvName",
            xmppServerHosts = listOf("xmppServeHost"),
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
        "xmppApi" {
            val xmppApi = XmppApi(jibriManager, listOf(xmppConfig), executorService)
            val mucClient: MucClient = mock()
            val mucClientProvider: MucClientProvider = { _: XMPPTCPConnectionConfiguration, _: String ->
                mucClient
            }
            val iqHandler = argumentCaptor<AbstractIqRequestHandler>()
            xmppApi.start(mucClientProvider)
            verify(mucClient).addIqRequestHandler(iqHandler.capture())

            "when receiving a start recording iq" {
                val jibriIq = createJibriIq(JibriIq.Action.START, JibriIq.RecordingMode.FILE)
                "and jibri is idle" {
                    whenever(jibriManager.startFileRecording(any(), any(), any(), any())).thenReturn(StartServiceResult.SUCCESS)
                    val response = iqHandler.firstValue.handleIQRequest(jibriIq)
                    val sentStanzas = argumentCaptor<Stanza>()
                    verify(mucClient).sendStanza(sentStanzas.capture())
                    should("send a pending response to the original IQ request") {
                        (response as JibriIq).status shouldBe JibriIq.Status.PENDING
                    }
                    should("send a success response") {
                        sentStanzas.allValues.size shouldBe 1
                        (sentStanzas.firstValue as JibriIq).status shouldBe JibriIq.Status.ON
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
                    whenever(jibriManager.startFileRecording(serviceParams.capture(), any(), any(), any())).thenReturn(StartServiceResult.SUCCESS)
                    iqHandler.firstValue.handleIQRequest(jibriIq)
                    should("parse and pass the app data") {
                        serviceParams.allValues.size shouldBe 1
                        serviceParams.firstValue.appData shouldBe appData
                    }
                }
            }
        }
    }
}
