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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.specs.ShouldSpec
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.StartServiceResult
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.config.XmppMuc
import org.jitsi.xmpp.mucclient.MucClient
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration

class XmppApiTest : ShouldSpec() {
    init {
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
            val xmppApi = XmppApi(jibriManager, listOf(xmppConfig))
            val mucClient: MucClient = mock()
            val mucClientProvider = { _: XMPPTCPConnectionConfiguration ->
                mucClient
            }
            val iqHandler = argumentCaptor<AbstractIqRequestHandler>()
            xmppApi.start(mucClientProvider)
            verify(mucClient).addIqRequestHandler(iqHandler.capture())

            "when receiving a start recording iq" {
                val jibriIq = JibriIq()
                jibriIq.recordingMode = JibriIq.RecordingMode.FILE
                jibriIq.action = JibriIq.Action.START
                //TODO: this can't go here, because it'll only be run once
                // for each of the should blocks below it, whereas we want it
                // to re-run for each nested test context below.  could we add a methods
                // like "forEachTest { .... }" and "runOnce { ... }" or something to
                // set this context?
                iqHandler.firstValue.handleIQRequest(jibriIq)
                "and jibri is busy" {
                    whenever(jibriManager.startFileRecording(any(), any(), any(), any())).thenReturn(StartServiceResult.BUSY)
                    val stanza = argumentCaptor<Stanza>()
                    try {
                        verify(mucClient, times(2)).sendStanza(stanza.capture())
                    } catch (t: Throwable) { println(t) }
                    should("send an error iq response") {
                        stanza.allValues.forEach { println(it) }
                    }
                }
                "and jibri is idle" {
                    // TODO
                }
            }
        }
    }
}
