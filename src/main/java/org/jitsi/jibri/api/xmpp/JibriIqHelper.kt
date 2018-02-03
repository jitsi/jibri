package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriStatusPacketExt
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Presence
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate

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

class JibriPresenceHelper {
    companion object {
        fun createPresence(status: JibriStatusPacketExt.Status, to: Jid): Presence {
            val jibriStatus = JibriStatusPacketExt()
            jibriStatus.status = status
            val pres = Presence(Presence.Type.available)
            pres.to = to
            pres.overrideExtension(jibriStatus)
            return pres
        }
    }
}