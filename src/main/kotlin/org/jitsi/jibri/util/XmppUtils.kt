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
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jxmpp.jid.EntityBareJid

/**
 * When we get a start [JibriIq] message, the room is given to us an [EntityBareJid] that we need to translate
 * into a URL for selenium to join.  This method translates that jid into the url.
 */
fun getCallUrlInfoFromJid(
    roomJid: EntityBareJid,
    stripFromRoomDomain: String,
    xmppDomain: String,
    baseUrl: String?
): CallUrlInfo {
    try {
        // The url domain is pulled from the xmpp domain of the connection sending the request
        var domain = roomJid.domain.toString()
        // But the room jid domain may have a subdomain that shouldn't be applied to the url, so strip out any
        // string we've been told to remove from the domain
        domain = domain.replaceFirst(stripFromRoomDomain, "", ignoreCase = true)
        // Now we need to extract a potential call subdomain, which will be anything that's left in the domain
        //  at this point before the configured xmpp domain.
        val subdomain = domain.subSequence(0, domain.indexOf(xmppDomain, ignoreCase = true)).trim('.')
        // Now just grab the call name
        val callName = roomJid.localpart.toString()

        // The call url is constructed from the baseCallUrl (base-url if any, or the xmpp domain), an optional subdomain, and a callname like so:
        // https://baseCallUrl/subdomain/callName
        var baseCallUrl = "https://$xmppDomain"
        if (!baseUrl.isNullOrEmpty()) {
            baseCallUrl = baseUrl
        }

        return when {
            subdomain.isEmpty() -> CallUrlInfo(baseCallUrl, callName)
            else -> CallUrlInfo("$baseCallUrl/$subdomain", callName)
        }
    } catch (e: Exception) {
        throw CallUrlInfoFromJidException(
            "Unable to extract call url info from Jid $roomJid (stripFromRoomDomain = $stripFromRoomDomain, " +
                "xmppDomain = $xmppDomain)"
        )
    }
}

class CallUrlInfoFromJidException(message: String) : Exception(message)
