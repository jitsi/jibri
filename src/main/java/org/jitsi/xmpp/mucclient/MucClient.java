/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.jitsi.xmpp.mucclient;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.iqrequest.IQRequestHandler;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.ping.PingManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The {@link MucClient} is responsible for handling a single xmpp
 * connection on which a single muc is joined
 * NOTE: Eventually this logic will reside in Jicoco, so this entire package
 * will go away once that is done)
 *
 * @author bbaldino
 */
public class MucClient
{
    private final Logger logger = Logger.getLogger(MucClient.class.toString());
    /**
     * The {@link AbstractXMPPConnection} object for the connection to
     * the xmpp server
     */
    private final AbstractXMPPConnection xmppConnection;
    /**
     * The {@link MultiUserChat} objects which represent the MUCs we're
     * connected to
     */
    private List<MultiUserChat> mucs = new ArrayList<>();

    /**
     * Connect to the xmpp service defined by the given config
     * @param config xmpp connection details
     * @throws Exception from {@link XMPPTCPConnection#connect()} or
     * {@link XMPPTCPConnection#login()}
     */
    public MucClient(XMPPTCPConnectionConfiguration config)
        throws Exception
    {
        PingManager.setDefaultPingInterval(30);
        xmppConnection = new XMPPTCPConnection(config);
        ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(xmppConnection);
        reconnectionManager.enableAutomaticReconnection();
        xmppConnection.addConnectionListener(new ConnectionListener()
        {
            @Override
            public void connected(XMPPConnection xmppConnection)
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: connected");
            }

            @Override
            public void authenticated(XMPPConnection xmppConnection, boolean b)
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: authenticated");
            }

            @Override
            public void connectionClosed()
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: closed");
            }

            @Override
            public void connectionClosedOnError(Exception e)
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: closed on error");
            }

            @Override
            public void reconnectionSuccessful()
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: reconnection successful");
            }

            @Override
            public void reconnectingIn(int i)
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: reconnecting in " + i);
            }

            @Override
            public void reconnectionFailed(Exception e)
            {
                logger.info("Xmpp connection status [" + xmppConnection.getXMPPServiceDomain() + "]: reconnection failed");
            }
        });
        xmppConnection.connect().login();
    }

    /**
     * Create and/or join the muc named mucJid with the given nickname
     * @param mucJid the jid of the muc to join
     * @param nickname the nickname to use when joining the muc
     * @throws Exception from {@link MultiUserChat#createOrJoin(Resourcepart)}
     */
    public void createOrJoinMuc(EntityBareJid mucJid, Resourcepart nickname)
            throws Exception
    {
        MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(xmppConnection);
        MultiUserChat muc = mucManager.getMultiUserChat(mucJid);
        muc.createOrJoin(nickname);
        mucs.add(muc);
    }

    /**
     * Adds the given handler as an iq request handler on the xmpp connection
     * @param iqRequestHandler the iq request handler to add to the connection
     */
    public void addIqRequestHandler(IQRequestHandler iqRequestHandler)
    {
        xmppConnection.registerIQRequestHandler(iqRequestHandler);
    }

    /**
     * Send an xmpp stanza on the xmpp connection
     * @param stanza the stanza to send
     * @return true if it is sent successfully, false otherwise
     */
    public boolean sendStanza(Stanza stanza)
    {
        try
        {
            xmppConnection.sendStanza(stanza);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
