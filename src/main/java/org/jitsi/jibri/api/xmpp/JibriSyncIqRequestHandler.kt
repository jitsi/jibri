package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XMPPError

abstract class JibriSyncIqRequestHandler : AbstractIqRequestHandler(
        JibriIq.ELEMENT_NAME,
        JibriIq.NAMESPACE,
        IQ.Type.set,
        IQRequestHandler.Mode.sync) {
    override fun handleIQRequest(iq: IQ): IQ {
        if (iq !is JibriIq) {
            return IQ.createErrorResponse(iq, XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))
        } else {
            return handleJibriIqRequest(iq)
        }
    }

    abstract fun handleJibriIqRequest(jibriIq: JibriIq): IQ
}