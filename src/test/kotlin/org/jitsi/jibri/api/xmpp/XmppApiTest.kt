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
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.beInstanceOf
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jitsi.jibri.JibriBusyException
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.config.XmppMuc
import org.jitsi.jibri.helpers.resetIoPool
import org.jitsi.jibri.helpers.setIoPool
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
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.XMPPError
import org.jxmpp.jid.impl.JidCreate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class XmppApiTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private fun createJibriIq(iqAction: JibriIq.Action, iqMode: JibriIq.RecordingMode? = null): JibriIq {
        return JibriIq().apply {
            iqMode?.let {
                recordingMode = it
            }
            action = iqAction
            // Note that the domain used below must match the ones in the xmpp env config
            from = JidCreate.from("from_jid@xmppDomain")
            to = JidCreate.from("to_jid@xmppDomain")
            room = JidCreate.entityBareFrom("room_jid@xmppDomain")
            sessionId = "session_id"
        }
    }

    init {
        val jibriManager: JibriManager = mockk(relaxed = true)
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
        val jibriStatusManager: JibriStatusManager = mockk(relaxed = true)
        // the initial status is idle
        val expectedStatus =
                JibriStatus(ComponentBusyStatus.IDLE, OverallHealth(ComponentHealthStatus.HEALTHY, mapOf()))
        every { jibriStatusManager.overallStatus } returns expectedStatus

        beforeSpec {
            val executorService: ExecutorService = mockk()
            every { executorService.submit(any<Runnable>()) } answers {
                firstArg<Runnable>().run()
                CompletableFuture<Unit>()
            }
            TaskPools.setIoPool(executorService)
        }

        afterSpec {
            TaskPools.resetIoPool()
        }

        context("xmppApi") {
            val xmppApi = XmppApi(jibriManager, listOf(xmppConfig), jibriStatusManager)
            val mucClientManager: MucClientManager = mockk(relaxed = true)
            // A dummy MucClient we'll use to be the one incoming messages are received on
            val mucClient: MucClient = mockk(relaxed = true)
            every { mucClient.id } returns xmppConfig.xmppServerHosts.first()
            xmppApi.start(mucClientManager)
            should("add itself as the IQ listener for the MUC client manager") {
                verify { mucClientManager.setIQListener(xmppApi) }
            }
            should("create a muc client for each xmpp host") {
                verify(exactly = 2) { mucClientManager.addMucClient(any()) }
            }

            context("when receiving a start recording iq") {
                val jibriIq = createJibriIq(JibriIq.Action.START, JibriIq.RecordingMode.FILE)
                context("and jibri is idle") {
                    val statusHandler = slot<JibriServiceStatusHandler>()
                    every { jibriManager.startFileRecording(any(), any(), any(), capture(statusHandler)) } just Runs
                    val response = xmppApi.handleIq(jibriIq, mucClient)
                    should("send a pending response to the original IQ request") {
                        response shouldNotBe null
                        response should beInstanceOf<JibriIq>()
                        response as JibriIq
                        response.status shouldBe JibriIq.Status.PENDING
                    }
                    context("after the service starts up") {
                        statusHandler.captured(ComponentState.Running)
                        should("send a success response") {
                            val sentStanzas = mutableListOf<Stanza>()
                            verify { mucClient.sendStanza(capture(sentStanzas)) }
                            sentStanzas.size shouldBe 1
                            sentStanzas.first().shouldBeInstanceOf<JibriIq> {
                                it.status shouldBe JibriIq.Status.ON
                            }
                        }
                        context("and it is stopped") {
//                            whenever(jibriManager.stopService()) doAnswer {}
                            val stopIq = createJibriIq(JibriIq.Action.STOP)
                            val stopResponse = xmppApi.handleIq(stopIq, mucClient)
                            should("respond correctly") {
                                verify { jibriManager.stopService() }
                                stopResponse shouldBeResponseTo stopIq
                                stopResponse.shouldBeInstanceOf<JibriIq> {
                                    it.status shouldBe JibriIq.Status.OFF
                                }
                            }
                        }
                    }
                }
                context("and jibri is busy") {
                    every { jibriManager.startFileRecording(any(), any(), any(), any()) } throws JibriBusyException()
                    should("send an error IQ") {
                        val response = xmppApi.handleIq(jibriIq, mucClient)
                        response.shouldBeInstanceOf<JibriIq> {
                            it.status shouldBe JibriIq.Status.OFF
                            it.failureReason shouldBe JibriIq.FailureReason.BUSY
                            it.shouldRetry shouldBe true
                        }
                    }
                }
                context("with application data") {
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

                    val serviceParams = mutableListOf<ServiceParams>()
                    every { jibriManager.startFileRecording(capture(serviceParams), any(), any(), any()) } just Runs
                    xmppApi.handleIq(jibriIq, mucClient)
                    should("parse and pass the app data") {
                        serviceParams.size shouldBe 1
                        serviceParams.first().appData shouldBe appData
                    }
                }
                context("from a muc client it doesn't recognize") {
                    val unknownMucClient: MucClient = mockk()
                    every { unknownMucClient.id } returns "unknown name"
                    val result = xmppApi.handleIq(jibriIq, unknownMucClient)
                    result.error shouldNotBe null
                    result.error.condition shouldBe XMPPError.Condition.bad_request
                }
            }
        }
    }
}

private infix fun IQ.shouldBeResponseTo(req: IQ) {
    from.toString() shouldBe req.to.toString()
    to.toString() shouldBe req.from.toString()
}
