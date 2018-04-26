# SIP Gateway
Currently the XMPP API is the only officially supported API, so this doc will discuss how to configure Jibri to allow SIP gateway calls via the XMPP API.

To use the SIP gateway functionality of Jibri, you'll need to set the `sip_control_muc` field in `config.json`, it can be found within the `XmppEnvironment` field.  Jibri will join this MUC to announce itself of being capable of providing SIP gateway services to Jicofo.
