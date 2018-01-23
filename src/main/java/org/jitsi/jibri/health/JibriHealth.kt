package org.jitsi.jibri.health

import com.fasterxml.jackson.annotation.JsonProperty

//TODO: make sure we match the old health object
// Check:
// 1) is jibri up at all
// 2) whether or not it's currently recording
// (the rest has been less useful)
data class JibriHealth(
        var busy: Boolean = false
//        var health: Boolean = false,
//        @JsonProperty("XMPPConnected")
//        var xmppConnected: Boolean = false,
//        // e.g. "meetjitsi", "hipchat", "stride", etc.
//        var environment: Map<String, String> = mapOf()
)