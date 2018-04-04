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

package org.jitsi.jibri.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.jitsi.jibri.util.WritableDirectory

data class XmppCredentials(
    val domain: String = "",
    val username: String = "",
    val password: String = ""
)

data class XmppMuc(
    val domain: String,
    @JsonProperty("room_name")
    val roomName: String,
    val nickname: String
)

data class XmppEnvironmentConfig(
    /**
     * A user-friendly name for this environment
     */
    val name: String,
    /**
     * A list of xmpp server hosts to which we'll connect
     */
    @JsonProperty("xmpp_server_hosts")
    val xmppServerHosts: List<String>,
    /**
     * The base xmpp domain
     */
    @JsonProperty("xmpp_domain")
    val xmppDomain: String,
    /**
     * The login information for the control API
     */
    @JsonProperty("control_login")
    val controlLogin: XmppCredentials,
    /**
     * The muc we'll join to announce our presence for
     * recording and streaming services
     */
    @JsonProperty("control_muc")
    val controlMuc: XmppMuc,
    /**
     * The muc we'll join to announce our presence
     * for sip gateway services
     * TODO: should this and controlMuc above be
     * optional?  but somehow require at least one
     * to be set?
     */
    @JsonProperty("sip_control_muc")
    val sipControlMuc: XmppMuc,
    /**
     * The login information the selenium web client will use
     */
    @JsonProperty("call_login")
    val callLogin: XmppCredentials,
    /**
     * The value we'll strip from the room jid domain to derive
     * the call url
     */
    @JsonProperty("room_jid_domain_string_to_strip_from_start")
    val stripFromRoomDomain: String,
    /**
     * How long Jibri sessions will be allowed to last before
     * they are stopped.  A value of 0 allows them to go on
     * indefinitely
     */
    @JsonProperty("usage_timeout")
    val usageTimeoutMins: Int,
    /**
     * Whether or not we'll automatically trust any
     * cert on this XMPP domain
     */
    @JsonProperty("always_trust_certs")
    val trustAllXmppCerts: Boolean = true
)

data class JibriConfig(
    @JsonProperty("recording_directory")
    val recordingDirectory: WritableDirectory,
    @JsonProperty("finalize_recording_script_path")
    val finalizeRecordingScriptPath: String,
    @JsonProperty("xmpp_environments")
    val xmppEnvironments: List<XmppEnvironmentConfig>
)
