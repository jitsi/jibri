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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.CallUrlInfo
import org.jxmpp.jid.impl.JidCreate

class XmppUtilsTest : ShouldSpec() {
    private val baseDomain = "brian.jitsi.net"
    init {
        context("getCallUrlInfoFromJid") {
            context("a basic room jid") {
                val expected = CallUrlInfo("https://$baseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@$baseDomain")
                should("convert to a call url correctly") {
                    getCallUrlInfoFromJid(jid, "", baseDomain) shouldBe expected
                }
            }
            context("a roomjid with a subdomain that should be stripped") {
                val expected = CallUrlInfo("https://$baseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.$baseDomain")
                should("convert to a call url correctly") {
                    getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain) shouldBe expected
                }
            }
            context("a roomjid with a call subdomain") {
                val expected = CallUrlInfo("https://$baseDomain/subdomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@mucdomain.subdomain.$baseDomain")
                getCallUrlInfoFromJid(jid, "mucdomain.", baseDomain) shouldBe expected
            }
            context("a basic muc room jid, domain contains part to be stripped") {
                // domain contains 'conference'
                val conferenceBaseDomain = "conference.$baseDomain"
                val expected = CallUrlInfo("https://$conferenceBaseDomain", "roomName")
                val jid = JidCreate.entityBareFrom("${expected.callName}@conference.$conferenceBaseDomain")
                should("convert to a call url correctly") {
                    // we want to strip the first conference from the jid
                    getCallUrlInfoFromJid(jid, "conference", conferenceBaseDomain) shouldBe expected
                }
            }
        }
    }
}
