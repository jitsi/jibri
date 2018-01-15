package org.jitsi.jibri.health

import com.fasterxml.jackson.annotation.JsonProperty

//TODO: make sure we match the old health object
data class JibriHealth(
        var health: Boolean = false,
        var recording: Boolean = false,
        @JsonProperty("XMPPConnected")
        var xmppConnected: Boolean = false,
        var environment: Map<String, String> = mapOf()
)