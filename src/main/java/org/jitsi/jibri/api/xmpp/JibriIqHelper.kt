package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jivesoftware.smack.packet.IQ

//TODO: this functionality should be added to JibriIq

/**
 * Create a result iq from the given [jibriIq]
 */
class JibriIqHelper {
    companion object {
        fun createResult(jibriIq: JibriIq): JibriIq {
            val result = JibriIq()
            result.type = IQ.Type.result
            result.stanzaId = jibriIq.stanzaId
            result.to = jibriIq.from
            return result
        }
    }
}
