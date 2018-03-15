package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriStatusPacketExt
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Presence
import org.jxmpp.jid.Jid

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

        fun create(from: Jid, type: IQ.Type, status: JibriIq.Status): JibriIq {
            val jibriIq = JibriIq()
            jibriIq.to = from
            jibriIq.type = type
            jibriIq.status = status

            return jibriIq
        }

        fun create(from: Jid, type: IQ.Type): JibriIq {
            return create(from, type, JibriIq.Status.UNDEFINED)
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
