package org.jitsi.jibri.config

import com.fasterxml.jackson.annotation.JsonProperty

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
         * The muc we'll join to announce our presence
         */
        @JsonProperty("control_muc")
        val controlMuc: XmppMuc,
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
        @JsonProperty("usage_timeout")
        val usageTimeoutMins: Int
)

data class JibriConfig(
        @JsonProperty("recording_directory")
        val recordingDirectory: String,
        @JsonProperty("finalize_recording_script_path")
        val finalizeRecordingScriptPath: String,
        @JsonProperty("xmpp_environments")
        val xmppEnvironments: List<XmppEnvironmentConfig>
)