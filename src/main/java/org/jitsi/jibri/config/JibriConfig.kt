package org.jitsi.jibri.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// Ignore any values we don't recognize in the config rather than choking
// TODO: needs this for now to ignore 'environments' (see TODO below) but
//  we'll have to decide if we want this in the long run or not
@JsonIgnoreProperties(ignoreUnknown = true)
data class JibriConfig(
        @JsonProperty("jidserver_prefix")
        val jidServerPrefix: String,
        @JsonProperty("mucserver_prefix")
        val mucServerPrefix: String,
        @JsonProperty("boshdomain_prefix")
        val boshDomainPrefix: String,
        val password: String,
        @JsonProperty("recording_directory")
        val recordingDirectory: String,
        @JsonProperty("jid_username")
        val jidUsername: String,
        @JsonProperty("roomname")
        val roomName: String,
        @JsonProperty("xmpp_domain")
        val xmppDomain: String,
        @JsonProperty("selenium_xmpp_prefix")
        val seleniumXmppPrefix: String,
        @JsonProperty("selenium_xmpp_username")
        val seleniumXmppUsername: String,
        @JsonProperty("selenium_xmpp_password")
        val seleniumXmppPassword: String,
        val servers: List<String>
        //TODO: looks like this is a nested object, but need to know what
        // its structure looks like
        //val environments: Any
)