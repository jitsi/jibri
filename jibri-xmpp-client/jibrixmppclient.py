#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
    Based on examples from SleekXMPP: https://github.com/fritzy/SleekXMPP
"""

import sleekxmpp
from sleekxmpp import Iq
from sleekxmpp.xmlstream import register_stanza_plugin, ElementBase
from sleekxmpp.xmlstream.handler import Callback
from sleekxmpp.xmlstream.matcher import StanzaPath

class JibriElement(ElementBase):
    name = 'jibri'
    namespace = 'http://jitsi.org/protocol/jibri'
    plugin_attrib = 'jibri'
class JibriStatusElement(ElementBase):
    name = 'jibri-status'
    namespace = 'http://jitsi.org/protocol/jibri'
    plugin_attrib = 'jibri-status'

class JibriXMPPClient(sleekxmpp.ClientXMPP):

    def __init__(self, jid, password, room, nick, roompass, iq_callback):
        sleekxmpp.ClientXMPP.__init__(self, jid, password)

        self.room = room
        self.nick = nick
        self.roompass = roompass
        self.jid = jid
        self.iq_callback = iq_callback

        self.add_event_handler("session_start", self.start)
        self.add_event_handler("muc::%s::got_online" % self.room,
                               self.muc_online)

        self.register_handler(
            Callback('Jibri IQ callback',
                     StanzaPath('iq@type=set/jibri'),
                     self.on_jibri_iq))

        register_stanza_plugin(Iq, JibriElement)
        register_stanza_plugin(Iq, JibriStatusElement)

    def on_jibri_iq(self, iq):
        self.iq_callback(self, iq)

    def start(self, event):
        self.get_roster()
        self.send_presence()
        self.plugin['xep_0045'].joinMUC(self.room,
                                        self.nick,
                                        password=self.roompass)
                                        #wait=True)

        #update our presence with
        #<jibri xmlns="http://jitsi.org/protocol/jibri status="idle" />
        presence = self.make_presence(pto=self.room)
        iq = self.Iq()
        iq['jibri-status']._setAttr('status', 'idle')
        presence.append(iq['jibri-status'])
        print('sending presence: %s' % presence)
        presence.send()

    def muc_online(self, presence):
        pass
