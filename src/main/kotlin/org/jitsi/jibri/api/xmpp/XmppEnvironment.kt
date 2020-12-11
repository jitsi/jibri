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

data class XmppEnvironment(
    /**
     * A user-friendly name for this environment
     */
    val name: String,
    /**
     * A list of xmpp server hosts to which we'll connect
     */
    val xmppServerHosts: List<String>,
    /**
     * The base xmpp domain
     */
    val xmppDomain: String,
    /**
     * The login information for the control API
     */
    val controlLogin: XmppCredentials,
    /**
     * The muc we'll join to announce our presence for
     * recording and streaming services
     */
    val controlMuc: XmppMuc,
    /**
     * The muc we'll join to announce our presence
     * for sip gateway services
     * TODO: should this and controlMuc above be
     * optional?  but somehow require at least one
     * to be set?
     */
    val sipControlMuc: XmppMuc?,
    /**
     * The login information the selenium web client will use
     */
    val callLogin: XmppCredentials,
    /**
     * The value we'll strip from the room jid domain to derive
     * the call url
     */
    val stripFromRoomDomain: String,
    /**
     * How long Jibri sessions will be allowed to last before
     * they are stopped.  A value of 0 allows them to go on
     * indefinitely
     */
    val usageTimeoutMins: Int,
    /**
     * Whether or not we'll automatically trust any
     * cert on this XMPP domain
     */
    val trustAllXmppCerts: Boolean = true
)

fun com.typesafe.config.Config.toXmppEnvironment(): XmppEnvironment =
    XmppEnvironment(
        name = getString("name"),
        xmppServerHosts = getStringList("xmpp-server-hosts"),
        xmppDomain = getString("xmpp-domain"),
        controlLogin = getConfig("control-login").toXmppCredentials(),
        controlMuc = getConfig("control-muc").toXmppMuc(),
        sipControlMuc = if (hasPath("sip-control-muc")) {
            getConfig("sip-control-muc").toXmppMuc()
        } else null,
        callLogin = getConfig("call-login").toXmppCredentials(),
        stripFromRoomDomain = getString("strip-from-room-domain"),
        usageTimeoutMins = getDuration("usage-timeout").toMinutes().toInt(),
        trustAllXmppCerts = getBoolean("trust-all-xmpp-certs")
    )

data class XmppMuc(
    val domain: String,
    val roomName: String,
    val nickname: String
)

fun com.typesafe.config.Config.toXmppMuc(): XmppMuc =
    XmppMuc(
        domain = getString("domain"),
        roomName = getString("room-name"),
        nickname = getString("nickname")
    )

data class XmppCredentials(
    val domain: String,
    val username: String,
    val password: String
)

fun com.typesafe.config.Config.toXmppCredentials(): XmppCredentials =
    XmppCredentials(
        domain = getString("domain"),
        username = getString("username"),
        password = getString("password")
    )
