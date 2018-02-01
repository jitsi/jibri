package org.jitsi.jibri.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// Ignore any values we don't recognize in the config rather than choking
// TODO: needs this for now to ignore 'environments' (see TODO below) but
//  we'll have to decide if we want this in the long run or not
@JsonIgnoreProperties(ignoreUnknown = true)
data class JibriConfig(
        /**
         * [jidServerPrefix] is the XMPP subdomain (on [xmppDomain])
         * that will be used for administrative 'users' (jicofo, jibri,
         * jigasi, etc) to log in
         */
        @JsonProperty("jidserver_prefix")
        val jidServerPrefix: String,
        /**
         * [mucServerPrefix] is the XMPP subdomain (on [xmppDomain])
         * which hosts the call muc
         * TODO: this is what needs to be stripped from the room jid to
         * get a url
         */
        @JsonProperty("mucserver_prefix")
        val mucServerPrefix: String,
        /**
         * [boshDomainPrefix] confirm this is no longer needed
         */
        //@JsonProperty("boshdomain_prefix")
        //val boshDomainPrefix: String,
        /**
         * [breweryPrefix] is the XMPP subdomain (on [xmppDomain])
         * which the jibri control muc is hosted
         */
        @JsonProperty("brewery_prefix")
        val breweryPrefix: String,
        /**
         * [password] is the password used along with [jidUsername]
         * which is used for the control muc login (which is on
         * [jidServerPrefix].[xmppDomain])
         */
        val password: String,
        /**
         * See [password]
         */
        @JsonProperty("jid_username")
        val jidUsername: String,
        /**
         * Directory in which file recordings will be temporarily stored.
         * Anything in here will be deleted after the recording is done
         * (after the finalize script runs)
         */
        @JsonProperty("recording_directory")
        val recordingDirectory: String,
        /**
         * The [roomName] on the control muc domain jibri should join
         */
        @JsonProperty("roomname")
        val roomName: String,
        /**
         * [xmppDomain] is the XMPP domain for all XMPP connections
         */
        @JsonProperty("xmpp_domain")
        val xmppDomain: String,
        /**
         * The XMPP subdomain for the jibri selenium user.
         * e.g. "recorder"
         */
        @JsonProperty("selenium_xmpp_prefix")
        val seleniumXmppPrefix: String,
        /**
         * The username to use to login to the jibri selenium xmpp subdomain
         */
        @JsonProperty("selenium_xmpp_username")
        val seleniumXmppUsername: String,
        /**
         * The password to use to login to the jibri selenium xmpp subdomain
         */
        @JsonProperty("selenium_xmpp_password")
        val seleniumXmppPassword: String,
        /**
         * List of XMPP hosts to connect to
         */
        val servers: List<String>,
        @JsonProperty("finalize_recording_script_path")
        val finalizeRecordingScriptPath: String,
        /**
         * How long this jibri is allowed to run any particular service
         * 0 means disabled and the service can run indefinitely
         */
        @JsonProperty("usage_timeout")
        val usageTimeout: Int = 0,
        /**
         * Path to the chrome binary
         */
        @JsonProperty("chrome_binary_path")
        val chromeBinaryPath: String
        //TODO: looks like this is a nested object, but need to know what
        // its structure looks like
        /**
         * each environment should only be considered valid if it has a 'servers' value,
         * but we'll change this to only include valid environment entries
         */
        //val environments: Any
        /**
         * Defines a new url template (as opposed to the default which is
         * https://
         */
        //val url: String
)