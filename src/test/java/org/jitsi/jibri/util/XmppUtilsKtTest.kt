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
    fun `test roomjid with subdomain url conversion`() {
        val expected = CallUrlInfo("https://$baseDomain", "roomName")
        val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.$baseDomain")

        expect(expected) { getCallUrlInfoFromJid(jid, "mucdomain.", "brian.jitsi.net") }
    }
}
