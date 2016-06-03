#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
    SleekXMPP: The Sleek XMPP Library
    Copyright (C) 2010  Nathanael C. Fritz
    This file is part of SleekXMPP.

    See the file LICENSE for copying permission.
"""

import logging
from optparse import OptionParser

import sleekxmpp
from sleekxmpp import Iq
from sleekxmpp.exceptions import XMPPError
from sleekxmpp.xmlstream import register_stanza_plugin

from jibrixmppclient import JibriElement

YSI = 'id-of-the-youtube-stream'

jibriiq = False
class ActionUserBot(sleekxmpp.ClientXMPP):

    """
    A simple SleekXMPP bot that sends a custom action stanza
    to another client.
    """

    def __init__(self, jid, password, other, room, nick, roompass, url):
        sleekxmpp.ClientXMPP.__init__(self, jid, password)

        self.action_provider = other
        self.room = room
        self.nick = nick
        self.roompass = roompass
        self.url = url

        # The session_start event will be triggered when
        # the bot establishes its connection with the server
        # and the XML streams are ready for use. We want to
        # listen for this event so that we we can initialize
        # our roster.
        self.add_event_handler("session_start", self.start, threaded=True)
        self.add_event_handler("message", self.message)

        register_stanza_plugin(Iq, JibriElement)

    def start(self, event):
        """
        Process the session_start event.

        Typical actions for the session_start event are
        requesting the roster and broadcasting an initial
        presence stanza.

        Arguments:
            event -- An empty dictionary. The session_start
                     event does not provide any additional
                     data.
        """
        self.send_presence()
        self.get_roster()
        self.plugin['xep_0045'].joinMUC(self.room,
                                        self.nick,
                                        password=self.roompass)
                                        #wait=True)

        self.send_custom_iq()

    def send_custom_iq(self):
        """Create and send two custom actions.

        If the first action was successful, then send
        a shutdown command and then disconnect.
        """
        iq = self.Iq()
        iq['to'] = self.action_provider
        iq['type'] = 'set'
        iq['jibri']._setAttr('action', 'start')
        iq['jibri']._setAttr('url', self.url)
        iq['jibri']._setAttr('streamid', YSI)
        #iq['jibri']._setAttr('token','token')
        global jibriiq
        jibriiq = iq

        try:
            logging.info("Sending IQ: %s" % iq)
            resp = iq.send()
            logging.info("Got response: %s" % resp)
            # The wait=True delays the disconnect until the queue
            # of stanzas to be sent becomes empty.
            self.disconnect(wait=True)
        except XMPPError:
            print('There was an error sending the custom action.')

    def message(self, msg):
        """
        Process incoming message stanzas.

        Arguments:
            msg -- The received message stanza.
        """
        logging.info(msg['body'])

if __name__ == '__main__':
    # Setup the command line arguments.
    optp = OptionParser()

    # Output verbosity options.
    optp.add_option('-q', '--quiet', help='set logging to ERROR',
                    action='store_const', dest='loglevel',
                    const=logging.ERROR, default=logging.INFO)
    optp.add_option('-d', '--debug', help='set logging to DEBUG',
                    action='store_const', dest='loglevel',
                    const=logging.DEBUG, default=logging.INFO)
    optp.add_option('-v', '--verbose', help='set logging to COMM',
                    action='store_const', dest='loglevel',
                    const=5, default=logging.INFO)

    # JID and password options.
    optp.add_option("-j", "--jid", dest="jid",
                    help="JID to use")
    optp.add_option("-p", "--password", dest="password",
                    help="password to use")
    optp.add_option("-o", "--other", dest="other",
                    help="JID providing custom stanza")
    optp.add_option("-r", "--room", dest="room",
                    help="MUC")
    optp.add_option("-n", "--nick", dest="nick",
                    help="MUC nick")
    optp.add_option("-u", "--url", dest="url",
                    help="url")
    optp.add_option("-R", "--roompass", dest="roompass",
                    help="MUC password")

    opts, args = optp.parse_args()

    # Setup logging.
    logging.basicConfig(level=opts.loglevel,
                        format='%(levelname)-8s %(message)s')

    if opts.jid is None:
        exit('no user')
    if opts.password is None:
        exit('no pass')
    if opts.url is None:
        exit('no url')
    if opts.other is None:
        opts.other = 'jibri@auth.boris.jitsi.net'
    if opts.nick is None:
        opts.nick = 'nick'
    if opts.room is None:
        exit('no room')
    if opts.roompass is None:
        opts.roompass = 'password'

    # Setup the CommandBot and register plugins. Note that while plugins may
    # have interdependencies, the order in which you register them does
    # not matter.
    xmpp = ActionUserBot(opts.jid, opts.password, opts.other, opts.room, opts.nick, opts.roompass, opts.url)
    #xmpp.register_plugin('xep_0030') # Service Discovery
    #xmpp.register_plugin('xep_0004') # Data Forms
    xmpp.register_plugin('xep_0045')  # Multi-User Chat
    #xmpp.register_plugin('xep_0050') # Adhoc Commands

    # If you are working with an OpenFire server, you may need
    # to adjust the SSL version used:
    # xmpp.ssl_version = ssl.PROTOCOL_SSLv3

    # If you want to verify the SSL certificates offered by a server:
    # xmpp.ca_certs = "path/to/ca/cert"

    # Connect to the XMPP server and start processing XMPP stanzas.
    if xmpp.connect():
        # If you do not have the dnspython library installed, you will need
        # to manually specify the name of the server if it does not match
        # the one in the JID. For example, to use Google Talk you would
        # need to use:
        #
        # if xmpp.connect(('talk.google.com', 5222)):
        #     ...
        xmpp.process(block=True)
        print("Done")
    else:
        print("Unable to connect.")
