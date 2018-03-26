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

package org.jitsi.jibri.util

import org.jitsi.jibri.CallUrlInfo
import org.jxmpp.jid.impl.JidCreate
import org.testng.annotations.Test
import kotlin.test.expect

class XmppUtilsKtTest {
    val baseDomain = "brian.jitsi.net"
    @Test
    fun `test basic roomjid url conversion`() {
        val expected = CallUrlInfo("https://$baseDomain", "roomName")
        val jid = JidCreate.entityBareFrom("${expected.callName}@$baseDomain")

        expect(expected) { getCallUrlInfoFromJid(jid, "", "brian.jitsi.net") }
    }

    @Test
    fun `test roomjid with subdomain strip url conversion`() {
        val expected = CallUrlInfo("https://$baseDomain", "roomName")
        val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.$baseDomain")

        expect(expected) { getCallUrlInfoFromJid(jid, "mucdomain.", "brian.jitsi.net") }
    }

    @Test
    fun `test roomjid with call subdomain url conversion`() {
        val expected = CallUrlInfo("https://$baseDomain/subdomain", "roomName")
        val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.subdomain.$baseDomain")

        expect(expected) { getCallUrlInfoFromJid(jid, "mucdomain.", "brian.jitsi.net") }
    }
}
