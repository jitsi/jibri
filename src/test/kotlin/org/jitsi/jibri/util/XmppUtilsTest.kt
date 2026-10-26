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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.error.BadRequestException
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException

class XmppUtilsTest : ShouldSpec() {
    private val baseDomain = "brian.jitsi.net"
    private val baseUrl = "http://brian.jitsi.net"

    init {
        context("getCallUrlInfoFromJid") {
            context("a basic room jid with no baseUrl set") {
                val expected = CallUrlInfo("https://$baseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@$baseDomain")
                should("convert to a call url correctly") {
                    getCallUrlInfoFromJid(jid, "", baseDomain, null) shouldBe expected
                }
            }
            context("a basic room jid with the baseUrl set") {
                val expected = CallUrlInfo(baseUrl, "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@$baseDomain")
                should("convert to a call url correctly") {
                    getCallUrlInfoFromJid(jid, "", baseDomain, baseUrl) shouldBe expected
                }
            }
            context("a basic room jid with empty baseUrl") {
                val expected = CallUrlInfo("https://$baseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@$baseDomain")
                should("convert to a call url correctly") {
                    getCallUrlInfoFromJid(jid, "", baseDomain, "") shouldBe expected
                }
            }
            context("a roomjid with a subdomain that should be stripped") {
                val expected = CallUrlInfo("https://$baseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.$baseDomain")
                should("convert to a call url correctly") {
                    getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain, "") shouldBe expected
                }
            }
            context("a roomjid with a call subdomain") {
                val expected = CallUrlInfo("https://$baseDomain/subdomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.subdomain.$baseDomain")
                getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain, "") shouldBe expected
            }
            context("a basic muc room jid, domain contains part to be stripped") {
                // domain contains 'conference'
                val conferenceBaseDomain = "conference.$baseDomain"
                val expected = CallUrlInfo("https://$conferenceBaseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@conference.$conferenceBaseDomain")
                should("convert to a call url correctly") {
                    // we want to strip the first conference from the jid
                    getCallUrlInfoFromJid(jid, "conference", conferenceBaseDomain, "") shouldBe expected
                }
            }
            context("a room jid") {
                should("set the authorized room to the room jid") {
                    val jid = JidCreate.entityBareFrom("my%20room@$baseDomain")
                    getCallUrlInfoFromJid(jid, "", baseDomain, null).authorizedRoom shouldBe jid
                }
                should("set the authorized room to the room jid with the tenant") {
                    val jid = JidCreate.entityBareFrom("roomName@mucdomain.tenant.$baseDomain")
                    getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain, "").authorizedRoom shouldBe
                        JidCreate.entityBareFrom("roomname@mucdomain.tenant.$baseDomain")
                }
            }
            context("a room name in the form jitsi-meet uses") {
                should("be accepted") {
                    forAll(
                        row("roomname"),
                        row("roomName"),
                        row("my%20room"),
                        row("caf%c3%a9"),
                        row("caf%C3%A9"),
                        row("a%3fb"),
                        row("__jicofo-health-check-abc")
                    ) { name ->
                        val jid = JidCreate.entityBareFrom("$name@$baseDomain")
                        getCallUrlInfoFromJid(jid, "", baseDomain, null).callName shouldBe name.lowercase()
                    }
                }
            }
            context("a room name in a form jitsi-meet does not use") {
                should("be refused") {
                    forAll(
                        row("myroom?x"),
                        row("myroom#config.startsilent=true"),
                        row("%61"),
                        row("a%2"),
                        row("room+x"),
                        row("caf%c3%89"),
                        row("a%ff"),
                        row("%ef%ac%81"),
                        row("café"),
                        row("."),
                        row(".."),
                        row("a%2fb"),
                        row("a%2Fb"),
                        // jitsi-meet removes these from the URL path before it decodes the name, so "myroom*" joins
                        // "myroom". encodeURIComponent keeps them, so a round trip alone does not detect them.
                        row("myroom*"),
                        row("myroom!"),
                        row("myroom()"),
                        row("myroom%2a")
                    ) { name ->
                        val jid = JidCreate.entityBareFrom("$name@$baseDomain")
                        shouldThrow<BadRequestException> {
                            getCallUrlInfoFromJid(jid, "", baseDomain, null)
                        }
                    }
                }
            }
            context("a tenant in the form jitsi-meet uses") {
                should("be accepted") {
                    forAll(
                        row("tenant"),
                        row("vpaas-magic-cookie-0123456789abcdef"),
                        row("my_tenant"),
                        row("caf\u00e9")
                    ) { tenant ->
                        val jid = JidCreate.entityBareFrom("room@mucdomain.$tenant.$baseDomain")
                        getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain, "").tenant shouldBe tenant
                    }
                }
            }
            context("a tenant with a character that JitsiXmppStringprep refuses") {
                should("not parse as a JID") {
                    // Smack drops such a request when it parses the IQ, so it never reaches getCallUrlInfoFromJid.
                    forAll(
                        row("tenant?x"),
                        row("tenant*")
                    ) { tenant ->
                        shouldThrow<XmppStringprepException> {
                            JidCreate.entityBareFrom("myroom@mucdomain.$tenant.$baseDomain")
                        }
                    }
                }
            }
            context("a tenant in a form jitsi-meet does not use") {
                should("be refused") {
                    forAll(
                        row("%61"),
                        row("x.y"),
                        row("a%2fb"),
                        row("caf%c3%a9")
                    ) { tenant ->
                        val jid = JidCreate.entityBareFrom("myroom@mucdomain.$tenant.$baseDomain")
                        shouldThrow<BadRequestException> {
                            getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain, "")
                        }
                    }
                }
            }
        }
    }
}
