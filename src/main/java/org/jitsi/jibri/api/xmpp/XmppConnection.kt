package org.jitsi.jibri.api.xmpp

import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart

class XmppConnection {
    init {
        val xmppConfig = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain("recorder.brian2.jitsi.net")
                .setHost("brian2.jitsi.net")
                .setUsernameAndPassword("recorder", "1@MT3hR3c0rd|_|r")
                .build()
        val xmppConnection = XMPPTCPConnection(xmppConfig)
        xmppConnection.connect().login()
        println("Connected")
        val mucJid: EntityBareJid = JidCreate.entityBareFrom("JibriBrewery@internal.auth.brian2.jitsi.net")
        val nickname = Resourcepart.from("jibri-12345")

        val mucManager = MultiUserChatManager.getInstanceFor(xmppConnection)
        val muc = mucManager.getMultiUserChat(mucJid)

        // muc is used just to announce 'existence' and for sending presence (about status)
        // jicofo will care about presence messages sent to the muc, but other pieces will only announce things to
        //  the muc (via presence) not consume anything coming from the muc.  anything they consume will come as a
        //  direct message
        // should be able to join/handle multiple mucs (when sending a message, always send to all mucs, when receiving
        // a message, it will already say which muc it's from)

        muc.createOrJoin(nickname)
        muc.addMessageListener() {
            println("received message $it")
        }
    }
}