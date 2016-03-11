#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
    Based on examples from SleekXMPP: https://github.com/fritzy/SleekXMPP
"""
import os
import time
import random
import logging
from optparse import OptionParser
from subprocess import call
import sys
from jibrixmppclient import JibriXMPPClient
from jibriselenium import JibriSeleniumDriver

jibriiq = False
running = False
clients = {}
client_in_use = None
controllerJid = None
js = None

# FIXME
launch_recording_script = os.getcwd() + "/../scripts/launch_recording.sh"
stop_recording_script = os.getcwd() + "/../scripts/stop_recording.sh"


def on_jibri_iq(client, iq):
    global running
    global jibriiq
    jibriiq = iq

    logging.info("on_jibri_iq: %s" % iq)

    reply = client.Iq()
    reply['to'] = iq['from']
    reply['id'] = iq['id']

    start = False
    stop = False
    action = iq['jibri']._getAttr('action')
    if action == 'start':
        if running:
            reply['type'] = 'error'
            reply['error']['text'] = 'Instance already in use.'
        else:
            if not iq['jibri']._getAttr('streamid'):
                reply['type'] = 'error'
                reply['error']['text'] = 'No stream-id.'
            elif not iq['jibri']._getAttr('url'):
                reply['type'] = 'error'
                reply['error']['text'] = 'No URL.'
            else:
                running = True
                reply['type'] = 'result'
                reply['jibri']._setAttr('state', 'pending')
                global controllerJid
                # We've found "the one". It's the first one. We're not choosy.
                controllerJid = iq['from']
                start = True
    elif action == 'stop':
        stop = True
        reply['type'] = 'result'
        reply['jibri']._setAttr('state', 'off')
    else:
        reply['type'] = 'error'
        reply['error']['text'] = 'Action not implemented.'
        logging.error("Action %s not implemented" % action)

    reply.send()

    if start:
        # Mark us as busy in the MUC presence:
        presence = client.make_presence(pto=client.room)
        tmp = client.Iq()
        tmp['jibri-status']._setAttr('status', 'busy')
        presence.append(tmp['jibri-status'])
        logging.info('sending presence: %s' % presence)
        presence.send()

        close_all_other_clients(client)

        global client_in_use
        client_in_use = client
        start_jibri(iq['jibri']._getAttr('url'),
                    iq['jibri']._getAttr('follow-entity'),
                    iq['jibri']._getAttr('streamid'))
        # TODO: notify of updates
    elif stop:
        call([stop_recording_script])
        logging.info("Stopping.")
        time.sleep(3)
        sys.exit(0)


def close_all_other_clients(c):
    pass

def get_full_url(conference_name, follow_entity, token=None):

    if '@' in conference_name:
        conference_name = conference_name[:conference_name.index('@')]
    if follow_entity:
        url += '&follow_entity=' + follow_entity + 
    if token:
        url += '&token=' + token

    return url

def start_jibri(url, follow_entity, stream_id, token='token'):
    retcode=0
    global js
    token='abc'
    #url = get_full_url(url, follow_entity)

    logging.info(
        "starting jibri, url=%s, youtube-stream-id=%s" % (
            url, stream_id))

    js = JibriSeleniumDriver(url,token)
    js.launchUrl()
    if js.waitXMPPConnected():
      if js.waitDownloadBitrate()>0:
        retcode = call([launch_recording_script, url, 'ignore', 'ignore', stream_id],
             shell=False)
      else:
        #didn't launch ffmpeg properly right
        retcode=1336

    else:
      #didn't launch chrome properly right
      retcode=1337

    if retcode > 0:
        # We failed to start, bail.
        logging.info("start returned retcode=" + str(retcode))
        update_jibri_status('off')
        return

    # this seems broken
    wait_until_done(js)
    # sys.exit(0)

def wait_until_done(js=None):
    # So far so good.
    ffmpeg_pid_file = "/var/run/jibri/ffmpeg.pid"
    try:
        ffmpeg_pid = int(open(ffmpeg_pid_file).read().strip())
    except:
        #oops it all went awry
        #quit chrome
        js.quit()
        #clean up ffmpeg and kill off any last pieces
        update_jibri_status('off')
        call([stop_recording_script])
        return

    try:
        os.kill(ffmpeg_pid, 0)
        update_jibri_status('on')
    except:
        #oops it all went awry
        #quit chrome
        js.quit()
        #clean up ffmpeg and kill off any last pieces
        update_jibri_status('off')
        call([stop_recording_script])            
        return

    while True:
        try:
            #check that ffmpeg is running
            os.kill(ffmpeg_pid, 0)
            #check that we're still receiving data
            if js.waitDownloadBitrate() == 0:
                #throw an error here
                raise ValueError('no data received by meet for recording')

            #nothing is wrong, so wait a bit
            time.sleep(5)
        except:
            #oops it all went awry
            #quit chrome
            js.quit()
            #clean up ffmpeg and kill off any last pieces
            update_jibri_status('off')
            call([stop_recording_script])            
            return


def update_jibri_status(status):
    iq = client_in_use.Iq()
    iq['to'] = controllerJid
    iq['type'] = 'get'
    iq['jibri']._setAttr('status', status)
    logging.info('sending status update: %s' % iq)
    try:
        iq.send(wait=False)
    except:
        logging.info("Failed to send status update.")




if os.environ.get('NICK') is not None:
  default_nick = os.environ.get('NICK')
else:
  default_nick = 'jibri-' + str(random.random())[2:]


optp = OptionParser()
optp.add_option('-q', '--quiet', help='set logging to ERROR',
                action='store_const', dest='loglevel',
                const=logging.ERROR, default=logging.INFO)
optp.add_option('-d', '--debug', help='set logging to DEBUG',
                action='store_const', dest='loglevel',
                const=logging.DEBUG, default=logging.INFO)
optp.add_option('-v', '--verbose', help='set logging to COMM',
                action='store_const', dest='loglevel',
                const=5, default=logging.INFO)

optp.add_option("-j", "--jid", dest="jid", help="JID to use")
optp.add_option("-p", "--password", dest="password", help="password to use")
optp.add_option("-r", "--room", dest="room", help="MUC room to join")
optp.add_option("-n", "--nick", dest="nick", help="MUC nickname",
                default=default_nick)
optp.add_option("-P", "--roompass", dest="roompass",
                help="password for the MUC")

optp.usage = 'Usage: %prog [options] <server_hostname1 server_hostname2 ...>'

opts, args = optp.parse_args()

# Setup logging.
logging.basicConfig(level=opts.loglevel,
                    format='%(asctime)s %(levelname)-8s %(message)s')

if opts.jid is None:
    if os.environ.get('JID') is None:
      optp.print_help()
      exit("No jid given.")
    else:
      opts.jid = os.environ.get('JID')
if opts.password is None:
    if os.environ.get('PASS') is None:
      optp.print_help()
      exit("No password given.")
    else:
      opts.password = os.environ.get('PASS')
if opts.room is None:
    if os.environ.get('ROOM') is None:
      optp.print_help()
      exit("No room given.")
    else:
      opts.room = os.environ.get('ROOM')
if opts.roompass is None:
    if os.environ.get('ROOMPASS') is not None:
        opts.roompass = os.environ.get('ROOMPASS')
if not args:
    if os.environ.get('SERVERS') is None:
      optp.print_help()
      exit("No hostnames given.")
    else:
      args = os.environ.get('SERVERS').split(" ")



for hostname in args:
    logging.info("Creating a client for hostname: %s" % hostname)
    c = JibriXMPPClient(
        opts.jid, opts.password, opts.room, opts.nick,
        roompass=opts.roompass,
        iq_callback=on_jibri_iq)
    # c.register_plugin('xep_0030')  # Service Discovery
    c.register_plugin('xep_0045')  # Multi-User Chat
    # c.register_plugin('xep_0199')  # XMPP Ping
    if c.connect((hostname, 5222)):
        c.process(block=False)
    clients[hostname] = c
