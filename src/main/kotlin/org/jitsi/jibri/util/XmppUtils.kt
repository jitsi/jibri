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
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jxmpp.jid.EntityBareJid
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

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
    val callName = roomJid.localpart.toString()
    if (!isBackendSafeRoomName(callName)) {
        throw BadRequestException("Room name '$callName' is not in the form jitsi-meet uses")
    }
    try {
        // The url domain is pulled from the xmpp domain of the connection sending the request
        var domain = roomJid.domain.toString()
        // But the room jid domain may have a subdomain that shouldn't be applied to the url, so strip out any
        // string we've been told to remove from the domain
        domain = domain.replaceFirst(stripFromRoomDomain, "", ignoreCase = true)
        // Now we need to extract a potential call subdomain, which will be anything that's left in the domain
        //  at this point before the configured xmpp domain.
        val subdomain = domain.subSequence(0, domain.indexOf(xmppDomain, ignoreCase = true)).trim('.')

        // The call url is constructed from the baseCallUrl (base-url if any, or the xmpp domain), an optional subdomain, and a callname like so:
        // https://baseCallUrl/subdomain/callName
        var baseCallUrl = "https://$xmppDomain"
        if (!baseUrl.isNullOrEmpty()) {
            baseCallUrl = baseUrl
        }

        return when {
            subdomain.isEmpty() -> CallUrlInfo(baseCallUrl, callName, "", authorizedRoom = callName)

            else -> {
                // Jitsi-meet maps a "." in the tenant to "_" in the MUC domain, so a tenant with a "." is never one that
                // jitsi-meet joins.
                if (subdomain.contains('.') || !isBackendSafeRoomName(subdomain.toString())) {
                    throw BadRequestException("Tenant '$subdomain' is not in the form jitsi-meet uses")
                }
                CallUrlInfo("$baseCallUrl/$subdomain", callName, subdomain.toString(), authorizedRoom = callName)
            }
        }
    } catch (e: BadRequestException) {
        throw e
    } catch (e: Exception) {
        throw CallUrlInfoFromJidException(
            "Unable to extract call url info from Jid $roomJid (stripFromRoomDomain = $stripFromRoomDomain, " +
                "xmppDomain = $xmppDomain)"
        )
    }
}

class CallUrlInfoFromJidException(message: String) : Exception(message)

private const val URI_COMPONENT_UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
private val ENCODED_URI_COMPONENT = Regex("(?:[A-Za-z0-9\\-_.!~*'()]|%[0-9A-Fa-f]{2})+")

/**
 * Whether [name] is a room name (or tenant) equal, ignoring case, to what jitsi-meet's getBackendSafeRoomName
 * produces. A name in any other form makes us join a room other than the one we were asked to join. For example, the
 * browser cuts "a?b" at the "?" and resolves ".." as a path, and jitsi-meet decodes "%61" to "a".
 */
fun isBackendSafeRoomName(name: String): Boolean {
    if (name == "." || name == ".." || !ENCODED_URI_COMPONENT.matches(name)) {
        return false
    }
    val decoded = URLDecoder.decode(name, Charsets.UTF_8)
    val normalized = Normalizer.normalize(decoded, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    // A web server can decode "%2f" before it splits the tenant from the room, so "a%2fb" can put us in tenant "a".
    // The check after the join compares only the room, so it cannot catch this.
    if (normalized.contains('/')) {
        return false
    }
    // XMPP nodeprep makes the localpart lowercase, so a name that differs only in case is the same room.
    return encodeUriComponent(normalized).equals(name, ignoreCase = true)
}

private fun encodeUriComponent(str: String): String = buildString {
    for (byte in str.toByteArray(Charsets.UTF_8)) {
        val b = byte.toInt() and 0xff
        if (b < 0x80 && b.toChar() in URI_COMPONENT_UNRESERVED) {
            append(b.toChar())
        } else {
            append("%%%02x".format(b))
        }
    }
}
