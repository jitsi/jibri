package org.jitsi.jibri.health

data class JibriHealth(
    /**
     * Whether or not this Jibri is "busy". See [JibriManager#busy]
     */
    var busy: Boolean = false
)
