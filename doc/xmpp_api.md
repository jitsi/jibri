# Jibri XMPP API

Jibri can be configured to join multiple XMPP environments which can be used as a control surface for leveraging Jibri services.  The configuration for these environments lives in config.json (a documented sample of config.json can be seen in the code [here](TODO)).  For a given XMPP environment, Jibri will:
* Connect to the provided XMPP domain on the provided XMPP host
* Login to a given auth domain with the given credentials
* Join a MUC on the given MUC domain with the given MUC jid using the given MUC nickname
* Publish its status (defined by the status packet extension [here](https://github.com/jitsi/jitsi/blob/master/src/net/java/sip/communicator/impl/protocol/jabber/extensions/jibri/JibriStatusPacketExt.java)

At this point it will await an IQ message (defined by the custom IQ [here](https://github.com/jitsi/jitsi/blob/master/src/net/java/sip/communicator/impl/protocol/jabber/extensions/jibri/JibriIq.java)) asking it to start or stop a given service.  Whenever Jibri's status changes, it will send a new presence to reflect its current state.
