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
 */

package org.jitsi.jibri.api.xmpp

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runBlockingTest
import org.jitsi.jibri.CallUrlInfoFromJidException
import org.jitsi.jibri.EmptyCallException
import org.jitsi.jibri.JibriBusy
import org.jitsi.jibri.JibriException
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.JibriSession
import org.jitsi.jibri.JibriState
import org.jitsi.jibri.ProcessHung
import org.jitsi.jibri.UnsupportedOsException
import org.jitsi.xmpp.extensions.health.HealthStatusPacketExt
import org.jitsi.xmpp.extensions.jibri.JibriBusyStatusPacketExt
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.xmpp.extensions.jibri.JibriStatusPacketExt
import org.jitsi.xmpp.mucclient.IQListener
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.packet.ErrorIQ
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.XMPPError
import org.jxmpp.jid.impl.JidCreate

class XmppApiTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    val mucClient: MucClient = mockk(relaxed = true) {
        every { id } returns xmppConfig.xmppServerHosts.first()
    }

    private val iqListener = slot<IQListener>()
    private val presenceUpdates = mutableListOf<ExtensionElement>()

    private val mucClientManager: MucClientManager = mockk(relaxed = true) {
        every { setIQListener(capture(iqListener)) } just Runs
        every { setPresenceExtension(capture(presenceUpdates)) } just Runs
    }
    private val jibriState = MutableStateFlow<JibriState>(JibriState.Idle)
    private val jibriManager: JibriManager = mockk {
        every { currentState } returns jibriState
    }

    init {
        runBlockingTest {
            context("XmppApi") {
                val xmppApi = XmppApi(jibriManager, listOf(xmppConfig), mucClientManager)
                xmppApi.joinMucs()

                should("create a muc client for each xmpp host") {
                    verify(exactly = 2) { mucClientManager.addMucClient(any()) }
                }

                should("register an IQ listener") {
                    iqListener.isCaptured shouldBe true
                }

                should("send a presence with the initial sate") {
                    presenceUpdates shouldHaveSize 1
                    with(presenceUpdates.first()) {
                        shouldBeInstanceOf<JibriStatusPacketExt>()
                        busyStatus.status shouldBe JibriBusyStatusPacketExt.BusyStatus.IDLE
                    }
                }

                context("whenever jibrimanager's state is updated") {
                    context("to busy") {
                        jibriState.value = JibriState.Busy(null)
                        advanceUntilIdle()
                        should("send presence") {
                            with(presenceUpdates.last()) {
                                shouldBeInstanceOf<JibriStatusPacketExt>()
                                busyStatus.status shouldBe JibriBusyStatusPacketExt.BusyStatus.BUSY
                                healthStatus.status shouldBe HealthStatusPacketExt.Health.HEALTHY
                            }
                        }
                        context("and then back to idle") {
                            jibriState.value = JibriState.Idle
                            advanceUntilIdle()
                            should("send presence") {
                                with(presenceUpdates.last()) {
                                    shouldBeInstanceOf<JibriStatusPacketExt>()
                                    busyStatus.status shouldBe JibriBusyStatusPacketExt.BusyStatus.IDLE
                                    healthStatus.status shouldBe HealthStatusPacketExt.Health.HEALTHY
                                }
                            }
                        }
                    }

                    context("to error") {
                        jibriState.value = JibriState.Error(UnsupportedOsException("Win 3.1"))
                        advanceUntilIdle()
                        should("send presence") {
                            with(presenceUpdates.last()) {
                                shouldBeInstanceOf<JibriStatusPacketExt>()
                                busyStatus.status shouldBe JibriBusyStatusPacketExt.BusyStatus.IDLE
                                healthStatus.status shouldBe HealthStatusPacketExt.Health.UNHEALTHY
                            }
                        }
                    }

                    context("to expired") {
                        jibriState.value = JibriState.Expired
                        advanceUntilIdle()
                        should("not send a presence") {
                            presenceUpdates shouldHaveSize 1
                        }
                    }
                }

                context("when receiving a start recording iq") {
                    val startRecordingIq = createStartRecordingIq()
                    context("and the session fails immediately") {
                        context("because jibri is busy") {
                            every { jibriManager.startFileRecordingSession(any(), any()) } throws JibriBusy
                            val response = iqListener.captured.handleIq(startRecordingIq, mucClient)

                            should("send an error IQ") {
                                response.shouldBeInstanceOf<JibriIq>()
                                response.status shouldBe JibriIq.Status.OFF
                                response.failureReason shouldBe JibriIq.FailureReason.BUSY
                                response.shouldRetry shouldBe true
                            }
                        }
                        context("with a request error") {
                            every { jibriManager.startFileRecordingSession(any(), any()) } throws
                                CallUrlInfoFromJidException("foo")

                            should("send an error signaling not to retry the request") {
                                val response = iqListener.captured.handleIq(startRecordingIq, mucClient)
                                response.apply {
                                    shouldBeInstanceOf<JibriIq>()
                                    status shouldBe JibriIq.Status.OFF
                                    failureReason shouldBe JibriIq.FailureReason.ERROR
                                    shouldRetry shouldBe false
                                }
                            }
                        }
                        context("with a system error") {
                            every { jibriManager.startFileRecordingSession(any(), any()) } throws
                                UnsupportedOsException("Win 3.1")

                            should("send an error IQ") {
                                val response = iqListener.captured.handleIq(startRecordingIq, mucClient)
                                response.apply {
                                    shouldBeInstanceOf<JibriIq>()
                                    status shouldBe JibriIq.Status.OFF
                                    failureReason shouldBe JibriIq.FailureReason.ERROR
                                    shouldRetry shouldBe true
                                }
                            }
                        }
                    }

                    val recordingSession = FakeJibriSession()
                    every { jibriManager.startFileRecordingSession(any(), any()) } returns recordingSession

                    val response = iqListener.captured.handleIq(startRecordingIq, mucClient)
                    should("send a pending response to the original IQ request") {
                        response shouldNotBe null
                        response.shouldBeResponseTo(startRecordingIq)
                        response.shouldBeInstanceOf<JibriIq>()
                        response.status shouldBe JibriIq.Status.PENDING
                    }

                    should("wait on the job to finished") {
                        recordingSession.numWaiters shouldBe 1
                    }

                    context("after the session starts up") {
                        recordingSession.running()
                        should("send an update") {
                            val sentStanzas = mutableListOf<Stanza>()
                            verify { mucClient.sendStanza(capture(sentStanzas)) }
                            sentStanzas.size shouldBe 1
                            with(sentStanzas.first()) {
                                shouldBeInstanceOf<JibriIq>()
                                status shouldBe JibriIq.Status.ON
                            }
                        }

                        context("and then it completes") {
                            context("with a session error") {
                                recordingSession.complete(ProcessHung("ffmpeg hung"))

                                should("send an appropriate update") {
                                    val sentStanzas = mutableListOf<Stanza>()
                                    verify { mucClient.sendStanza(capture(sentStanzas)) }
                                    with(sentStanzas.last()) {
                                        shouldBeInstanceOf<JibriIq>()
                                        status shouldBe JibriIq.Status.OFF
                                        failureReason shouldBe JibriIq.FailureReason.ERROR
                                        shouldRetry shouldBe true
                                    }
                                }
                            }

                            context("cleanly") {
                                recordingSession.complete(EmptyCallException)

                                should("send an appropriate update") {
                                    val sentStanzas = mutableListOf<Stanza>()
                                    verify { mucClient.sendStanza(capture(sentStanzas)) }
                                    with(sentStanzas.last()) {
                                        shouldBeInstanceOf<JibriIq>()
                                        status shouldBe JibriIq.Status.OFF
                                        failureReason shouldBe null
                                        shouldRetry shouldBe null
                                    }
                                }
                            }
                        }

                        context("and then a stop IQ is received") {
                            val stopIq = createStopIq()
                            val stopResponse = iqListener.captured.handleIq(stopIq, mucClient)

                            should("stop the session") {
                                recordingSession.isCancelled() shouldBe true
                            }

                            should("respond correctly") {
                                val sentStanzas = mutableListOf<Stanza>()
                                verify { mucClient.sendStanza(capture(sentStanzas)) }
                                stopResponse shouldBeResponseTo stopIq
                                stopResponse.shouldBeInstanceOf<JibriIq>()
                                stopResponse.status shouldBe JibriIq.Status.OFF
                            }
                        }

                        context("and a stop IQ with the wrong session ID is received") {
                            val stopIq = createStopIq("some_other_session")
                            val stopResponse = iqListener.captured.handleIq(stopIq, mucClient)

                            should("send an error response") {
                                stopResponse shouldBeError XMPPError.Condition.bad_request
                            }
                        }
                    }
                    context("from a muc client it doesn't recognize") {
                        val unknownMucClient: MucClient = mockk()
                        every { unknownMucClient.id } returns "unknown name"
                        val result = iqListener.captured.handleIq(startRecordingIq, unknownMucClient)

                        should("respond with an error") {
                            result shouldBeError XMPPError.Condition.bad_request
                        }
                    }
                }
            }
        }
    }
}

private val xmppConfig = XmppEnvironment(
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

private fun createStartRecordingIq(): JibriIq = createJibriIq(JibriIq.Action.START, iqMode = JibriIq.RecordingMode.FILE)

private fun createStopIq(): JibriIq = createJibriIq(JibriIq.Action.STOP)
private fun createStopIq(sessionId: String): JibriIq = createJibriIq(JibriIq.Action.STOP, sessionId = sessionId)

private fun createJibriIq(
    iqAction: JibriIq.Action,
    sessionId: String = "session_id",
    iqMode: JibriIq.RecordingMode? = null
): JibriIq {
    return JibriIq().apply {
        iqMode?.let {
            recordingMode = it
        }
        action = iqAction
        // Note that the domain used below must match the ones in the xmpp env config
        from = JidCreate.from("from_jid@xmppDomain")
        to = JidCreate.from("to_jid@xmppDomain")
        room = JidCreate.entityBareFrom("room_jid@xmppDomain")
        this.sessionId = sessionId
    }
}

private class FakeJibriSession : JibriSession {
    private val deferred = CompletableDeferred<Unit>()
    private var onRunningBlock: (() -> Unit)? = null
    var numWaiters = 0
        private set

    override suspend fun await() {
        numWaiters++
        deferred.await()
    }

    override fun cancel(reason: String) = deferred.cancel(reason)

    override suspend fun onRunning(block: () -> Unit) {
        onRunningBlock = block
    }

    fun complete(t: JibriException) = deferred.completeExceptionally(t)

    fun running() = onRunningBlock?.invoke()

    fun isCancelled(): Boolean = deferred.isCancelled
}

private infix fun IQ.shouldBeResponseTo(req: IQ) {
    from.toString() shouldBe req.to.toString()
    to.toString() shouldBe req.from.toString()
}

private infix fun IQ.shouldBeError(condition: XMPPError.Condition) {
    shouldBeInstanceOf<ErrorIQ>()
    error.condition shouldBe condition
}
