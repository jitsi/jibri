#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
    Based on examples from SleekXMPP: https://github.com/fritzy/SleekXMPP
"""

import sleekxmpp
import logging
import threading
from sleekxmpp import Iq
from queue import Queue, Empty
from sleekxmpp.xmlstream import register_stanza_plugin, ElementBase, scheduler
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

    def __init__(self, hostname, jid, password, room, nick, roompass, loop, jibri_start_callback, jibri_stop_callback, jibri_health_callback, recording_lock, signal_queue):
        sleekxmpp.ClientXMPP.__init__(self, jid, password)

        self.hostname = hostname
        self.room = room
        self.nick = nick
        self.roompass = roompass
        self.jid = jid
        self.jibri_start_callback = jibri_start_callback
        self.jibri_stop_callback = jibri_stop_callback
        self.jibri_health_callback = jibri_health_callback
        self.recording_lock = recording_lock
        self.queue = signal_queue
        self.loop = loop
        self.controllerJid = ''
        self.asyncio_init_flag = False

        self.add_event_handler("session_start", self.start)
        self.add_event_handler("muc::%s::got_online"%self.room,
                               self.muc_online)

        self.register_handler(
            Callback('Jibri IQ callback',
                     StanzaPath('iq@type=set/jibri'),
                     self.on_jibri_iq))


        register_stanza_plugin(Iq, JibriElement)
        register_stanza_plugin(Iq, JibriStatusElement)

    def on_jibri_iq(self, iq):
#        global running
#        global jibriiq
#        jibriiq = iq

        logging.info("on_jibri_iq: %s" % iq)

        reply = self.Iq()
        reply['to'] = iq['from']
        reply['id'] = iq['id']

        start = False
        stop = False
        action = iq['jibri']._getAttr('action')
        if action == 'start':
            #we hope for threadsafe, so don't block and just tell jicofo no....
            if not self.recording_lock.acquire(False):
                logging.info("Unable to acquire a lock, instance is already in use")
                reply = self.make_iq_error(iq['id'], condition='service-unavailable', text='Instance already in use.', ito=iq['from'], iq=reply)
                reply['error']['code']='503'
                start = False
            else:
                if not iq['jibri']._getAttr('streamid') and not iq['jibri']._getAttr('sipaddress'):
                    logging.info("No stream provided")
                    reply = self.make_iq_error(iq['id'], condition='service-unavailable', text='No streamid or sipaddress specified.', ito=iq['from'], iq=reply)
                    reply['error']['code']='501'
                    self.recording_lock.release()
                    start = False
                elif not iq['jibri']._getAttr('room') and not iq['jibri']._getAttr('url'):
                    logging.info("No URL or Room provided")
                    reply = self.make_iq_error(iq['id'], condition='service-unavailable', text='No URL or room specified.', ito=iq['from'], iq=reply)
                    reply['error']['code']='501'
                    self.recording_lock.release()
                    start = False
                else:
                    running = True
                    reply['type'] = 'result'
                    reply['jibri']._setAttr('state', 'pending')
                    # We've found "the one". It's the first one. We're not choosy.
                    self.controllerJid = iq['from']
                    start = True
        elif action == 'stop':
            stop = True
            reply['type'] = 'result'
            reply['jibri']._setAttr('state', 'stopping')
        else:
            reply['type'] = 'error'
            reply['error']['text'] = 'Action not implemented.'
            logging.error("Action %s not implemented" % action)

        reply.send()

        if start:
            # Mark us as busy in the MUC presence:
            self.presence_busy()
            global client_in_use
            client_in_use = self
            # msg received, call the msg callback in the main thread with the event loop
            # this nets out a call to start_recording(client, url, follow_entity, stream_id)
            self.start_jibri(iq)

            #callback to parent thread to start jibri
            # TODO: notify of updates
        elif stop:
            logging.info("Stopping.")
            # msg received
            # call the stop callback in a new thread
            # this nets out a call to stop_recording('xmpp_stop')
            self.stop_jibri('xmpp_stop')

    def start_jibri(self,iq):
        backup_stream = iq['jibri']._getAttr('backup_stream')
        if backup_stream and backup_flag == 'true':
            backup_flag = 'backup'
        else:
            backup_flag = ''

        self.loop.call_soon_threadsafe(self.jibri_start_callback, self, iq['jibri']._getAttr('url'),iq['jibri']._getAttr('streamid'),iq['jibri']._getAttr('sipaddress'),iq['jibri']._getAttr('displayname'),iq['jibri']._getAttr('room'),iq['jibri']._getAttr('token'), backup_flag)

    def stop_jibri(self, reason='xmpp_stop'):
        #old way, didn't always run?  not sure why
        #self.loop.call_soon_threadsafe(self.jibri_stop_callback, 'xmpp_stop')
        #begin the stopping of jibri in a totally separate thread
        t=threading.Thread(None, target=self.jibri_stop_callback, kwargs=dict(status=reason))
        t.start()

    def handle_queue_msg(self, msg):
        if msg == None:
            #got the message to quit, so stand down
            logging.info("Attempting to disconnect and abort client...")
            self.disconnect(wait=True)
            self.abort()
            logging.info("Finished abort processing")
            return
        if msg == False:
            #got the message to quit, so stand down
            logging.info("Attempting to disconnect and abort client...")
            self.disconnect(wait=True)
            self.abort()
            logging.info("Finished abort processing")
            return

        if msg:
            if msg.startswith('error'):
                error_parts = msg.split('|')
                if len(error_parts) == 2:
                    error_type=error_parts[1]
                else:
                    error_type='unknown'
                self.report_jibri_error(error_type)
            if msg == 'health':
                 self.loop.call_soon_threadsafe(self.jibri_health_callback, self)
            if msg == 'idle':
                self.presence_idle()
            elif msg == 'busy': 
                self.presence_busy()
            elif msg == 'off':
                self.update_jibri_status('off')
            elif msg == 'on':
                self.update_jibri_status('on')
            elif msg == 'stopped':
                try:
                    recording_lock.release()
                except Exception:
                    pass
                self.update_jibri_status('off')
            elif msg == 'started':
                self.update_jibri_status('on')

    def from_main_thread_nonblocking(self):
#        logging.info("Checking queue")
        try:
            msg = self.queue.get(False) #doesn't block
            logging.info("got msg from main: %s" % msg)
            # schedule the reply
            scheduler.Task("HANDLE REPLY", 0, self.handle_queue_msg, (msg,)).run()
        except Empty:
            pass

    def start(self, event):
        logging.info("Starting up client %s, connecting to room %s as %s"%(self, self.room,self.nick))
        logging.debug("Getting roster for client %s"%self)
        self.get_roster()
        #announce ourselves
        logging.debug("Sending initial presence for client %s"%self)
        self.send_presence()

        #join the MUC specified
        logging.debug("Attempting to join MUC now for client %s"%self)
        self.plugin['xep_0045'].joinMUC(self.room,
                                        self.nick,
                                        password=self.roompass)
                                        #wait=True)

        #update our presence with
        #<jibri xmlns="http://jitsi.org/protocol/jibri status="idle" />
        logging.debug("Sending initial idle presence for client %s"%self)
        self.presence_idle()

        #only do this once, as it errors out with a ValueError: Key asyncio_queue already exists
        if not self.asyncio_init_flag:
            #run every second and check the queue from the main thread
            logging.debug("Adding asyncio queue check to the scheduler for client %s"%self)
            self.scheduler.add("asyncio_queue", 1, self.from_main_thread_nonblocking,
                repeat=True, qpointer=self.event_queue)
            self.asyncio_init_flag = True

        logging.info("Started up client %s"%self)


    def presence_busy(self):
        presence = self.make_presence(pto=self.room)
        tmp = self.Iq()
        tmp['jibri-status']._setAttr('status', 'busy')
        presence.append(tmp['jibri-status'])
        logging.info('sending presence: %s' % presence)
        presence.send()

    def presence_idle(self):
        presence = self.make_presence(pto=self.room)
        iq = self.Iq()
        iq['jibri-status']._setAttr('status', 'idle')
        presence.append(iq['jibri-status'])
        logging.info('sending presence: %s' % presence)
        presence.send()

    def report_jibri_error(self, error):
        iq = self.Iq()
        iq['jibri']._setAttr('status', 'failed')
        iq['to'] = self.controllerJid
        iq._setAttr('type','set')

        if error == 'selenium_start_stuck':
            error_text='Startup error: Selenium stuck'
        elif error == 'startup_exception':
            error_text='Startup error: Startup exception'
        elif error == 'startup_selenium_error':
            error_text='Startup error: Selenium error'
        elif error == 'ffmpeg_startup_exception':
            error_text='Startup error: FFMPEG fatal exception'
        elif error == 'startup_ffmpeg_error':
            error_text='Startup error: FFMPEG fatal error'
        elif error == 'startup_ffmpeg_streaming_error':
            error_text='Youtube request timeout'
        elif error == 'selenium_stuck':
            error_text='Streaming Error: Selenium stuck'
        elif error == 'selenium_died':
            error_text='Streaming Error: Selenium died'
        elif error == 'ffmpeg_died':
            error_text='Streaming Error: ffmpeg died'
        elif error == 'pjsua_died':
            error_text='Gateway Error: pjsua died'
        else:
            error_text='Unknown error'

        iq_error = self.make_iq_error(iq['id'], type='wait', condition='remote-server-timeout', text=error_text, ito=self.controllerJid)
        iq_error['error']['code']='504'

        iq['jibri'].append(iq_error['error'])

        logging.info('sending status update: %s' % iq)
        try:
            iq.send()
        except Exception as e:
            logging.error("Failed to send status update: %s", str(e))

    def update_jibri_status(self, status):
        iq = self.Iq()
        iq['to'] = self.controllerJid
        iq['type'] = 'set'
        iq['jibri']._setAttr('status', status)
        logging.info('sending status update: %s' % iq)
        try:
            iq.send()
        except Exception as e:
            logging.error("Failed to send status update: %s", str(e))

    def muc_online(self, presence):
        logging.info("Got online into a MUC for client %s: presence recived: %s"%(self,presence))
