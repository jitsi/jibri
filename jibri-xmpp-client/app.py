#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
    Based on examples from SleekXMPP: https://github.com/fritzy/SleekXMPP
"""
import asyncio
import os
import time
import random
import logging
import threading
import signal
import functools
import atexit
import requests
from optparse import OptionParser
from subprocess import call
from queue import Queue, Empty
from datetime import datetime, timedelta
import sys
from jibrixmppclient import JibriXMPPClient
from jibriselenium import JibriSeleniumDriver

#by default, stop recording automatically after 1 hour
default_timeout = 3600

#rest token
default_rest_token='abc123'


global queues
global js
global chrome_binary_path
chrome_binary_path=None
clients = {}
queues = {}

client_in_use = None
enso_token_suffix = ""
js = None

health_lock = threading.Lock()
recording_lock = threading.Lock()
loop = asyncio.get_event_loop()

# FIXME
launch_recording_script = os.getcwd() + "/../scripts/launch_recording.sh"
check_ffmpeg_script = os.getcwd() + "/../scripts/check_ffmpeg.sh"
stop_recording_script = os.getcwd() + "/../scripts/stop_recording.sh"

pidfile = '/var/run/jibri/jibri-xmpp.pid'
def writePidFile():
    global pidfile
    try:
        pid = str(os.getpid())
        f = open(pidfile, 'w')
        f.write(pid)
        f.close()
        atexit.register(deletePidFile)
    except FileNotFoundError as e:
        logging.warn("Unable to write pidfile: %s, %s"%(pidfile, e))
    except:
        raise

def deletePidFile():
    global pidfile
    if os.path.exists(pidfile):
        try:
            pid = str(os.getpid())
            file_pid = str(open(pidfile).read().strip())
            if pid == file_pid:
                os.remove(pidfile)
        except FileNotFoundError as e:
            logging.warn("Unable to find pidfile: %s, %s"%(pidfile, e))
        except:
            raise


#handled in main thread
#when we receive a SIGTERM we should just exit as cleanly as we can, without regard for locks
def sigterm_handler(loop=None):
    logging.warn("Received SIGTERM...")
    #do the real killing here
    kill_theworld(loop)
    #exit with a termination error
    logging.info("Finished SIGTERM processing...")
    sys.exit("Jibri terminated!")

#handled in main thread
#when we receive a SIGHUP we should wait until we can get the recording lock (so recording has ended), then exit
def sighup_handler(loop=None):
    global recording_lock
    logging.warn("Received SIGHUP")
    logging.info("SIGHUP, waiting on recording lock")
    with recording_lock:
        logging.info("SIGHUP, received recording lock")
        #do the real killing here
        kill_theworld(loop)
        #exit without an error
        logging.info("Finished SIGHUP processing...")
        sys.exit(None)


def sigusr1_handler(loop=None):
#    global queues
    logging.warn("Received SIGUSR1")
    update_jibri_status('busy')
    logging.warn("FINISHED SIGUSR1")

#basic reset function
def reset_recording():
    global recording_lock
    kill_ffmpeg_process()
    kill_selenium_driver()
    #send a false to stop watching ffmpeg/selenium and restart the loop
    queue_watcher_start(False)
    stop_recording()
    update_jibri_status('stopped')
    try:
        recording_lock.release()
        success=True
    except:
        success=False

#this calls a bash scripts which kills external processes and includes any AWS stop-recording hooks
def stop_recording():
     call([stop_recording_script])

def kill_ffmpeg_process():
    ffmpeg_pid_file = "/var/run/jibri/ffmpeg.pid"
    try:
        ffmpeg_pid = int(open(ffmpeg_pid_file).read().strip())
        os.kill(ffmpeg_pid)
    except:
        return False
    return True        

#ungraceful kill
def definitely_kill_selenium_driver():
    logging.info("Killing selenium driver after kill timed out waiting for graceful shutdown")
    stop_recording()

# kill selenium driver if it exists
def kill_selenium_driver():
    global js
    kill_timeout=5
    if js:
        #first start a thread that will kill selenium for reals if this blocks
        t=threading.Timer(kill_timeout, definitely_kill_selenium_driver)
        t.start()
        try:
            #selenium driver sometimes dies with an exception, which we can handle
            js.quit()
        except Exception as e:
            logging.info("Exception quitting selenium driver %s"%e)

        try:
            t.cancel()
        except Exception as e:
            logging.debug("Exception cancelling definitely_kill_selenium_driver thread timer %s"%e)

    js = False


#shut it all down!
def kill_theworld(loop=None):
    global clients
    kill_selenium_driver()
    stop_recording()
    #send a poisoned job to ffmpeg/selenium watcher to kill that thread
    queue_watcher_start(None)
    #send a poisoned job to the sleekxmpp threads
    update_jibri_status(None)
    for c in clients:
        clients[c].auto_reconnect = False
        #send abort message to every client
        clients[c].disconnect(wait=True)
        clients[c].abort()
    #end the final asyncio loop
    requests.post('http://localhost:5000/jibri/kill')
    loop.stop()

def jibri_health_callback(client):
    logging.info('Jibri health callback response from client %s'%client)
    global health_lock
    try:
        health_lock.release()
    except Exception as e:
        logging.debug('Exception releasing health lock: %s'%e)

#main callback to start jibri: meant to be run in the main thread, kicked off using a loop.call_soon_threadsafe() from within another thread (XMPP or REST thread)
def jibri_start_callback(client, url, stream_id, room=None, token='token', backup=''):
    global js
    global loop
    global opts
    if room:
        at_index = room.rfind('@')
        if at_index > 0:
            room = room[0:at_index]
        if not url:
            url = opts.url
        url = url.replace('%ROOM%',room)

    logging.info("Start recording callback")
    #mark everyone else as busy
    update_jibri_status('busy',client)

    logging.info("Starting jibri")
    retcode=9999
    try:
        retcode = start_jibri_selenium(url, token)
        if retcode == 0:
            #we started selenium on the first try!
            #we got a selenium, so start ffmpeg
            retcode = start_ffmpeg(stream_id, backup)
    except Exception as e:
        #oops it all went awry
        #quit chrome
        #clean up ffmpeg and kill off any last pieces
        logging.info("Jibri Startup exception: %s"%e)
        jibri_stop_callback('startup_exception')
        return
    if retcode > 0:
        # We failed to start, bail.
        logging.info("start returned retcode=" + str(retcode))
        jibri_stop_callback('startup_error')
    else:
        ffmpeg_pid_file = "/var/run/jibri/ffmpeg.pid"
        try:
            ffmpeg_pid = int(open(ffmpeg_pid_file).read().strip())
        except:
            #oops it all went awry
            #quit chrome
            #clean up ffmpeg and kill off any last pieces
            jibri_stop_callback('ffmpeg_startup_error')
            return

        try:
            success = check_ffmpeg_running()
            attempt_count=0
            attempt_max=15
            while not success:
                if attempt_count > attempt_max:
                    logging.warn("FFMPEG failed to start after %s attempts"%attempt_count)
                    success = False
                    raise ValueError('FFMPEG Failed to start after %s attempts'%attempt_count)
                    break

                time.sleep(1)
                success = check_ffmpeg_running()
                attempt_count=attempt_count+1

            if success:
                update_jibri_status('started')
                queue_watcher_start(stream_id)
                logging.info("queued job for jibri_watcher, startup completed...")
        except Exception as e:
            #oops it all went awry
            #clean up ffmpeg and kill off any last pieces
            logging.warn("Exception occured: %s"%e)
            jibri_stop_callback('ffmpeg_startup_exception')
            return

def queue_watcher_start(msg):
    logging.info("queueing job for jibri_watcher")
    global watcher_queue
    watcher_queue.put(msg)

def start_jibri_selenium(url, token='token'):
    retcode=0
    global js
    global chrome_binary_path
    token='abc'
    url = "%s#config.iAmRecorder=true"%url

    logging.info(
        "starting jibri selenium, url=%s" % (
            url))

    js = JibriSeleniumDriver(url,token,binary_location=chrome_binary_path)
    js.launchUrl()
    if js.waitXMPPConnected():
      if js.waitDownloadBitrate()>0:
        #everything is AWESOME!
        retcode=0
      else:
        #didn't launch ffmpeg properly right
        retcode=1336

    else:
      #didn't launch chrome properly right
      retcode=1337

    return retcode

def start_ffmpeg(stream_id, backup=''):
    logging.info("starting jibri ffmpeg with youtube-stream-id=%s" % stream_id)
    return call([launch_recording_script, 'ignore', 'ignore', 'ignore', stream_id, backup],
             shell=False)

def jibri_stop_callback(status=None):
    logging.info("jibri_stop_callback run with status %s"%status)
    reset_recording()
    update_jibri_status('idle')

def update_jibri_status(status, c=None):
    logging.info("update_jibri_status")
    global queues
    for hostname in queues:
        logging.info("looping through queue for host %s"%hostname)
        if not c or c.address[0] != hostname:
            logging.info("queueing status %s for host %s"%(status,hostname))
            queues[hostname].put(status)

#function to start the ffmpeg watcher thread..only meant to be run once
def start_jibri_watcher(queue, loop, finished_callback, timeout=0):
    t = threading.Thread(target=jibri_watcher, args=(queue, loop, finished_callback, timeout),name="jibri_watcher")
    t.daemon = True
    t.start()

#main function for jibri_watcher thread: waits on a queue until triggered
#thread then watches a running ffmpeg process until it completes, then triggers a callback
def jibri_watcher(queue, loop, finished_callback, timeout=0):
    while True:
        logging.info("jibri_watcher starting up...")
        msg = queue.get() #blocks waiting on a new job
        result = True
        selenium_result = True
        if msg == None:
            #done here, so exit
            logging.debug("jibri_watcher got poisoned job, exiting thread.")
            return
        elif (msg == False):
            logging.debug("jibri_watcher got reset job, restarting thread.")
            result = False
        else:
            logging.debug("jibri_watcher got msg from main: %s" % msg)

        queue.task_done()
        #now start looping to watch this ffmpeg process
        task_start=0
        if timeout:
            task_started=datetime.now()

        logging.info("jibri_watcher received job, now watching ffmpeg and selenium with a timeout of %s."%timeout)
        while (result and selenium_result):
            try:
                msg = queue.get(False) #doesn't block
            except Empty:
                msg = True
                pass
            if (msg == None):
                logging.debug("jibri_watcher got poisoned job, exiting thread.")
                return
            elif (msg == False):
                logging.debug("jibri_watcher got reset job, restarting thread.")
                result=False
                break


            #now we check if we should stop running because of a timeout
            if int(timeout)>0:
                if (datetime.now() - task_started) >= timedelta(seconds=int(timeout)):
                    #time to stop recording and reset the thread
                    logging.info("jibri_watcher ran past the recording timeout of %s."%timeout)
                    loop.call_soon_threadsafe(finished_callback, 'timelimit')
                    result=False
                    break

            result = check_ffmpeg_running()
            selenium_result = check_selenium_running()

            if not selenium_result:
                logging.info("Received a failure checking if selenium is running, checking again...")
                #try at least 2 more times
                selenium_result = check_selenium_running()
                if not selenium_result:
                    logging.info("Received a second failure checking if selenium is running, checking again...")
                    selenium_result = check_selenium_running()

            if result and selenium_result:
                logging.debug("ffmpeg and selenium still running, sleeping...")
                time.sleep(5)
            else:                
                logging.info("ffmpeg or selenium no longer running, triggering callback")
                if not result:
                    reason = 'ffmpeg_died'
                if not selenium_result:
                    reason = 'selenium_died'
                loop.call_soon_threadsafe(finished_callback, reason)


#utility function called by jibri_watcher, checks for the selenium process, returns true if the driver object is defined and shows connected to selenium
def check_selenium_running():
    global js
    selenium_timeout=10
    if not js:
        return False
    else:
        #first start a thread to ensure we stop everything if needed
        t = threading.Timer(selenium_timeout, jibri_stop_callback, kwargs=dict(status='selenium_stuck'))
        t.start
        running= js.checkRunning()
        try:
            t.cancel()
        except Exception as e:
            logging.info("Failed to cancel stop callback thread timer inside check_selenum_running: %s"%e)
        return running


#utility function called by jibri_watcher, checks for the ffmpeg process, returns true if the pidfile can be found and the process exists
def check_ffmpeg_running():
    ffmpeg_pid_file = "/var/run/jibri/ffmpeg.pid"
    ffmpeg_output_file="/tmp/jibri-ffmpeg.out"
    try:
        ffmpeg_pid = int(open(ffmpeg_pid_file).read().strip())
    except:
        #oops it all went awry
        #quit chrome
        #clean up ffmpeg and kill off any last pieces
        return None

    try:
        #check that ffmpeg is running
        os.kill(ffmpeg_pid, 0)
        #check if we are streaming
        retcode = call([check_ffmpeg_script, ffmpeg_output_file])
        if retcode > 0:
            logging.info('No frame= lines found from ffmpeg, not running yet')
            return False
        #nothing is wrong, so wait a bit
        return True
    except:
        #oops it all went awry
        #quit chrome
        return False



def start_sleekxmpp(jid, password, room, nick, roompass, loop, jibri_start_callback, jibri_stop_callback, jibri_health_callback, recording_lock, signal_queue):
    global clients
    logging.info("Creating a client for hostname: %s" % hostname)
    c = JibriXMPPClient(
        jid, password, room, nick,
        roompass=roompass,
        loop=loop,
#        iq_callback=on_jibri_iq,
        jibri_start_callback=jibri_start_callback,
        jibri_stop_callback=jibri_stop_callback,
        jibri_health_callback=jibri_health_callback,
        recording_lock=recording_lock,
        signal_queue=signal_queue)
    # c.register_plugin('xep_0030')  # Service Discovery
    c.register_plugin('xep_0045')  # Multi-User Chat
    # c.register_plugin('xep_0199')  # XMPP Ping
    if c.connect((hostname, 5222)):
        c.process(block=False)
        clients[hostname] = c



from flask import Flask, jsonify, request
from subprocess import call
from os import chdir, getcwd

app = Flask(__name__)

#call to begin recording
@app.route('/jibri/api/v1.0/start', methods=['POST','GET'])
def url_start_recording():
    global rest_token
    if request.method == 'POST':
        url = request.json['url']
        stream = request.json['stream']
        token = request.json['token']
    else:
        url = request.args['url']
        stream = request.args['stream']
        token = request.args['token']
    if not url or not stream:
        result = {'success': False, 'error':'Bad Parameters', 'request':request}
        return jsonify(result)
    else:
        if rest_token == token:
            global recording_lock
            if recording_lock.acquire(False):
                retcode=jibri_start_callback(None, url, stream)
                if retcode == 0:
                    result = {'success': success, 'url':url, 'stream':stream, 'token':token}
                else:
                    success = False
                    result = {'success': success, 'jibriseleniumerror':True, 'url':url, 'stream':stream, 'token':token}
            else:
                success = False
                result = {'success': success, 'error': 'Already recording'}                    
        else:
            success = False
            result = {'success': success, 'error': 'Token does not match'}
        return jsonify(result)

@app.route('/jibri/kill', methods=['POST'])
def kill():
    logging.info('Received kill signal for flask server')
    func = request.environ.get('werkzeug.server.shutdown')
    if func is None:
        raise RuntimeError('Not running with the Werkzeug Server')
    func()
    return "Shutting down..."

@app.route('/jibri/api/v1.0/stop', methods=['POST','GET'])
def url_stop_recording():
    global recording_lock
    reset_recording()

    success = True
    result = {'success': success}
    return jsonify(result)

#TODO: make this actually check something?
@app.route('/jibri/health', methods=['GET'])
def url_health_check():
    global recording_lock
    global health_lock
    global js

    #put an item on the XMPP queue, and watch for a return by callback
    result={'recording':recording_lock.locked(), 'health':False, 'XMPPConnected':False}
    if js:
        if js.checkRunning():
            result['selenium_health']=True
            try:
                result['XMPPConnected'] = js.waitXMPPConnected(timeout=10)
            except Exception as e:
                logging.info("Exception: %s"%e)
                pass
        else:
            result['selenium_health']=False
    else:
        result['selenium_health']=''

    result['jibri_xmpp']=check_xmpp_running()

    #only return good if we are connected via jibri, and not recording OR recording with good selenium health
    if result['jibri_xmpp'] and (not result['recording'] or (result['recording'] and result['selenium_health'])):
        result['health'] = True

    return jsonify(result)


def check_xmpp_running():
    update_jibri_status('health')
    #wait 2 seconds for a callback from the XMPP client threads
    xmpp_health_lock_retries = 5
    try:
        #first acquire the lock
        if health_lock.acquire(False):
            #ok it's acquired, now keep checking to see if it becomes unlocked outside our thread
            health_wait=0
            while health_lock.locked():
                if health_wait>xmpp_health_lock_retries:
                    logging.info('Health check timeout')
                    break
                #still locked, sleep
                health_wait=health_wait+1
                logging.debug('Health check waiting on XMPP client response')
                time.sleep(3)

            if health_wait>xmpp_health_lock_retries:
                logging.info('Health check never responded, XMPP failure')
                try:
                    health_lock.release()
                except Exception as e:
                    logging.debug("Exception: %s"%e)
                    pass
                return False
            else:
                #unlocked elsewhere, so we know the xmpp health callback worked as expected
                logging.debug('Health check unlocked outside thread, healthy!')
                return True
        else:
            logging.info('Health lock already locked, so cannot lock it again')
    except Exception as e:
        logging.error('Exception: %e'%e)
        raise


if __name__ == '__main__':
    #first things first, write ourselves a pidfile
    writePidFile()

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
    optp.add_option("-u", "--url", dest="url", help="URL to record, with token %ROOM% for room name: https://meet.jit.si/%ROOM%")
    optp.add_option("-p", "--password", dest="password", help="password to use")
    optp.add_option("-r", "--room", dest="room", help="MUC room to join")
    optp.add_option("-n", "--nick", dest="nick", help="MUC nickname",
                    default=default_nick)
    optp.add_option("-t", "--timeout", dest="timeout", help="Max usage in seconds before restarting",
                    default=default_timeout)
    optp.add_option("-P", "--roompass", dest="roompass",
                    help="password for the MUC")

    optp.add_option("-k", "--resttoken", dest="rest_token", help="Token to control rest start messages",
                    default=default_rest_token)

    optp.add_option("-b", "--chrome-binary", dest="chrome_binary_path", help="Path to chrome binary (defaults to chrome in PATH)",
                    default=None)


    optp.usage = 'Usage: %prog [options] <server_hostname1 server_hostname2 ...>'

    global opts
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
    if opts.url is None:
        if os.environ.get('URL') is None:
          optp.print_help()
          exit("No url given.")
        else:
          opts.url = os.environ.get('URL')
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

    if os.environ.get('TIMEOUT') is not None:
      opts.timeout = os.environ.get('TIMEOUT')


    if os.environ.get('REST_TOKEN') is not None:
      opts.rest_token = os.environ.get('REST_TOKEN')

    if os.environ.get('CHROME_BINARY') is not None:
      opts.chrome_binary_path = os.environ.get('CHROME_BINARY')

    if not args:
        if os.environ.get('SERVERS') is None:
          optp.print_help()
          exit("No hostnames given.")
        else:
          args = os.environ.get('SERVERS').split(" ")

    global rest_token
    rest_token = opts.rest_token

    if opts.chrome_binary_path:
        chrome_binary_path = opts.chrome_binary_path
        logging.info("Overriding chrome binary with value: %s"%chrome_binary_path)

    #handle SIGHUP graceful shutdown
    loop.add_signal_handler(signal.SIGHUP,sighup_handler, loop)

    #handle SIGTERM as immediate shutdown
    loop.add_signal_handler(signal.SIGTERM,sigterm_handler, loop)

    #debugging signal
    loop.add_signal_handler(signal.SIGUSR1,sigusr1_handler, loop)

# no longer debug
#    loop.set_debug(True)

    watcher_queue = Queue()

    for hostname in args:
        queues[hostname] = Queue()
        loop.run_in_executor(None, start_sleekxmpp, opts.jid, opts.password, opts.room, opts.nick, opts.roompass, loop, jibri_start_callback, jibri_stop_callback, jibri_health_callback, recording_lock, queues[hostname])

    #now start flask
    loop.run_in_executor(None, functools.partial(app.run, host='0.0.0.0'))
    loop.run_in_executor(None, start_jibri_watcher, watcher_queue, loop, jibri_stop_callback, opts.timeout)
    loop.run_forever()
    loop.close()
