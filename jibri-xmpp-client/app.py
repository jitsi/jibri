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
import json

from optparse import OptionParser
from subprocess import call
from queue import Queue, Empty
from datetime import datetime, timedelta
import sys
from jibrixmppclient import JibriXMPPClient
from jibriselenium import JibriSeleniumDriver

#by default, never stop recording automatically
default_timeout = None

#rest token, required to be passed in when accessing the service via REST API
default_rest_token='abc123'

#nggyu, video used to check for audio levels from loopback
default_audio_url = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ'


#collection of queues for use with each client
global queues
#javascript driver object
global js

#path to chrome on the filesystem, like /usr/bin/google-chrome-beta
global chrome_binary_path
chrome_binary_path=None

#username and password for authenticating to google before acting as a jibri
global google_account
global google_account_password
global current_environment
global selenium_xmpp_login
global selenium_xmpp_password
global active_client
global default_display_name
global default_email
#flag to control whether we launch with ffmpeg or pjsua
global pjsua_flag

global recording_directory

#return code storage for sip signaling
#TBD: do this a better way
global pjsua_failure_code

pjsua_failure_code=''

pjsua_flag=False

active_client=None
current_environment=''
default_display_name='Live Stream'
default_email='recorder@jitsi.org'
google_account=None
google_account_password=None
selenium_xmpp_login=None
selenium_xmpp_password=None
recording_directory='./recordings'

ffmpeg_pid_file = "/var/run/jibri/ffmpeg.pid"
ffmpeg_output_file="/tmp/jibri-ffmpeg.out"

pjsua_pid_file = "/var/run/jibri/pjsua.pid"
pjsua_output_file="/tmp/jibri-pjsua.log"
pjsua_result_file="/tmp/jibri-pjsua.result"


#initialize our collections
clients = {}
queues = {}

#init empty selenium driver
js = None

#start with a global lock for health checking and recording
health_lock = threading.Lock()
recording_lock = threading.Lock()

#do most things in an async event loop on the main thread
loop = asyncio.get_event_loop()

# Paths to external scripts for different shell-related tasks
launch_recording_script = os.getcwd() + "/../scripts/launch_recording.sh"
launch_gateway_script = os.getcwd() + "/../scripts/launch_gateway.sh"
check_ffmpeg_script = os.getcwd() + "/../scripts/check_ffmpeg.sh"
stop_recording_script = os.getcwd() + "/../scripts/stop_recording.sh"
finalize_recording_script = os.getcwd() + "/../scripts/finalize_recording.sh"
stop_selenium_script = os.getcwd() + "/../scripts/stop_selenium.sh"
check_audio_script = os.getcwd() + "/../scripts/check_audio.sh"

#our pidfile
#@TODO: make this more dynamic
pidfile = '/var/run/jibri/jibri-xmpp.pid'

#utility function for writing pid on startup
#includes a registration of the delete pid function on atexit for cleanup
def writePidFile():
    global pidfile
    try:
        pid = str(os.getpid())
        with open(pidfile, 'w') as f:
            f.write(pid)
        atexit.register(deletePidFile)
    except FileNotFoundError as e:
        logging.warn("Unable to write pidfile: %s, %s"%(pidfile, e))
    except:
        raise

#utility function for writing pid on startup
#registered to run atexit in the writePidFile function above
def deletePidFile():
    global pidfile
    try:
        pid = str(os.getpid())
        with open(pidfile) as f:
            file_pid = str(f.read().strip())
        if pid == file_pid:
            os.remove(pidfile)
    except FileNotFoundError as e:
        logging.warn("Unable to find pidfile: %s, %s" % (pidfile, e))


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

#handle a usr1 by sending a busy signal
#@TODO: decide what to really do on USR1
def sigusr1_handler(loop=None):
#    global queues
    logging.warn("Received SIGUSR1")
    update_jibri_status('busy')
    logging.warn("FINISHED SIGUSR1")

def reset_selenium():
    #kill selenium gracefully if possible
    kill_selenium_driver()

#basic reset function
#used to return jibri to a state where it can record again
def reset_recording():
    global recording_lock
    #first kill ffmpeg, to ensure that streaming stops before everything else
    kill_ffmpeg_process()
    kill_pjsua_process()

    #send a false to stop watching ffmpeg/selenium and restart the loop
    queue_watcher_start(False)

    #kill selenium gracefully if possible, by force otherwise
    reset_selenium()

    #final catchall, run the killall shell scripts to FORCE stop anything that didn't die gracefully above
    stop_recording()

#this calls a bash scripts which finalizes any recording transfers
def finalize_recording():
    global pjsua_flag
    global recording_directory
    #run any final processing tasks before finishing up, if not in pjsua mode
    if not pjsua_flag:
        call([finalize_recording_script,recording_directory])

def release_recording():
    global recording_lock
    #let the XMPP clients know we're stopped now
    update_jibri_status('stopped')

    #if we were locked, we shouldn't be anymore so unlock us
    try:
        recording_lock.release()
        success=True
    except:
        success=False

#this calls a bash scripts which kills external processes and includes any AWS stop-recording hooks
def stop_recording():
     call([stop_recording_script])

#this calls a bash scripts which kills only chrome/chromedriver
def stop_selenium():
     call([stop_selenium_script])

#this attempts to kill the ffmpeg process that jibri launched gracefully via os.kill
def kill_ffmpeg_process():
    try:
        with open(ffmpeg_pid_file) as f:
            ffmpeg_pid = int(f.read().strip())
        os.kill(ffmpeg_pid)
    except:
        return False
    return True

#this attempts to kill the pjsua process that jibri launched gracefully via os.kill
def kill_pjsua_process():
    pjsua_pid_file = "/var/run/jibri/pjsua.pid"
    try:
        with open(pjsua_pid_file) as f:
            pjsua_pid = int(f.read().strip())
        os.kill(pjsua_pid)
    except:
        return False
    return True

#ungraceful kill selenium using shell script with killall
#this function is run in a Timer thread while attempting to stop selenium the graceful way
#if the graceful shutdown works, we cancel the timer.  Otherwise
#Otherwise if the timer is reached, then we kill selenium via shell here
#This presumably also eventually unblocks the thread which tried to do the graceful shutdown using kill_selenium_driver
def definitely_kill_selenium_driver():
    logging.info("Killing selenium driver after kill timed out waiting for graceful shutdown")
    stop_selenium()

# kill selenium driver if it exists
#also kick off a thread to ensure that chrome is killed via shell if not via the driver
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
#this function is meant to immediately cause the shutdown of jibri
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

#this function is meant to be run by the XMPP client thread upon receipt of a 'health' message on the queue
#The callback then releases the health check lock, which was locked by the thread attempting to check for health
#The checking thread determines the health of the XMPP thread by checking the lock after a period if time
#if the lock is still set, then we know that no XMPP threads have handled the 'health' message, and are blocked
def jibri_health_callback(client):
    logging.info('Jibri health callback response from client %s'%client)
    global health_lock
    try:
        health_lock.release()
    except Exception as e:
        logging.debug('Exception releasing health lock: %s'%e)

#main callback to start jibri: meant to be run in the main thread, kicked off using a loop.call_soon_threadsafe() from within another thread (XMPP or REST thread)
def jibri_start_callback(client, url, recording_mode='file', stream_id='', sipaddress=None, displayname=None, room=None, token='token', backup='', recording_name=''):
    global js
    global loop
    global opts
    global client_opts
    global active_client
    global google_account_password
    global google_account
    global current_environment
    global chrome_binary_path
    global selenium_xmpp_login
    global selenium_xmpp_password
    global default_display_name
    global default_email
    global pjsua_flag
    global recording_directory

    #by default assume there's no subdomain for URL
    subdomain=''
    #make sure we remove whitespace from all input parameters
    room=room.strip()
    url=url.strip()
    stream_id=stream_id.strip()
    sipaddress=sipaddress.strip()

    #assume we're streaming if a stream_id is provided
    if stream_id:
        recording_mode='stream'

    #default to file-based recording if none is specified
    if not recording_mode:
        recording_mode = 'file'

    #DEBUG: REMOVE, forces file recording
#    recording_mode='file'

    #clear stream id if file mode is set
    if recording_mode == 'file':
        stream_id = ''

    #set a room_name default
    room_name=room

    mucserver_prefix='conference.'
    xmpp_domain=None
    c_google_account=None
    c_google_account_password=None
    c_chrome_binary_path=None
    c_xmpp_login=None
    c_xmpp_password=None
    c_display_name=default_display_name
    c_email=default_email
    boshdomain=None

    if google_account:
        c_google_account = google_account
    if google_account_password:
        c_google_account_password = google_account_password
    if chrome_binary_path:
        c_chrome_binary_path = chrome_binary_path

    if selenium_xmpp_login:
        c_xmpp_login = selenium_xmpp_login
    if selenium_xmpp_password:
        c_xmpp_password = selenium_xmpp_password

    if client:
        active_client = client
        co = client_opts[client.hostname]
        if co:
            logging.info('Client options for host: %s'%co)
        if 'google_account' in co:
            c_google_account = co['google_account']
            logging.info("Setting selenium google account from client options: %s"%c_google_account)

        if 'google_account_password' in co:
            c_google_account_password = co['google_account_password']
            logging.info("Setting selenium google account password from client options")

        if 'chrome_binary_path' in co:
            c_chrome_binary_path = co['chrome_binary_path']
            logging.info("Setting chrome binary path from client options: %s"%c_chrome_binary_path)

        if 'selenium_xmpp_login' in co:
            c_xmpp_login = co['selenium_xmpp_login']
            logging.info("Setting xmpp login from client options: %s"%c_xmpp_login)

        if 'selenium_xmpp_password' in co:
            c_xmpp_password = co['selenium_xmpp_password']
            logging.info("Setting xmpp password from client options")

        if 'boshdomain' in co:
            boshdomain = co['boshdomain']
            logging.info("Setting boshdomain from client options %s"%boshdomain)

        if 'environment' in co:
            current_environment = co['environment']

        if 'xmpp_domain' in co:
            xmpp_domain = co['xmpp_domain']
            logging.info("Setting xmpp_domain from client options %s"%xmpp_domain)

        if 'displayname' in co:
            c_display_name = co['displayname']

        if 'email' in co:
            c_email = co['email']

        if 'pjsua_flag' in co:
            pjsua_flag = co['pjsua_flag']
            logging.info("Setting pjsua_flag from client options %s"%pjsua_flag)

        if 'mucserver_prefix' in co:
            mucserver_prefix = co['mucserver_prefix']
            logging.info("Setting mucserver_prefix from client options %s"%mucserver_prefix)

        if 'recording_directory' in co:
            recording_directory = co['recording_directory']
            logging.info("Setting recording_directory from client options %s"%recording_directory)


    #when we're using pjsua, override the default display name to be the sip address or passed in display name
    if pjsua_flag:
        if displayname:
            c_display_name = displayname
        else:
            c_display_name = sipaddress
        c_email = sipaddress

    if room:
        at_index = room.rfind('@')
        if at_index > 0:
            #truncate the room name to just the name part of the JID
            room_name = room[0:at_index]
            room_host = room[at_index+1:]
            if room_host and room_host.startswith(mucserver_prefix) and room_host.endswith(xmpp_domain) and room_host != '%s%s'%(mucserver_prefix,xmpp_domain):
                #detect a subdomain if our room host matches the xmpp_domain and mucserver_prefix but also contains more information
                #example is conference.foo.xmpp_domain.org
                subdomain = room_host.split('.')[1]
                #if we found a subdomain, then append a / to separate it from the rest of the URL
                if subdomain:
                    subdomain = subdomain+'/'

        #no url was passed in explicitly, so look it up by client
        if client and not url:
            if client.hostname in client_opts:
                co = client_opts[client.hostname]
                if 'url' in co and co['url']:
                    url = co['url']

        url = url.replace('%SUBDOMAIN%',subdomain)
        url = url.replace('%ROOM%',room_name)

    if not recording_name:
        recording_name = url[len('https://'):]

    recording_path = recording_directory + '/'+recording_name
    try:
        os.makedirs(recording_path)
    except OSError as exc:  # Python >2.5
        if exc.errno == errno.EEXIST and os.path.isdir(recording_path):
            pass
        else:
            raise

    logging.info("Start recording callback")
    #mark everyone else as busy
    update_jibri_status('busy',client)

    logging.info("Starting jibri")
    #wait 30 seconds for start of selenium, otherwise kill it
    selenium_timeout=30

    #begin attempting to launch selenium
    attempt_count=0
    attempt_max=3
    while attempt_count<attempt_max:
        retcode=9999
        attempt_count=attempt_count+1
        logging.info("Starting selenium attempt %d/%d pjsua_flag:%s"%(attempt_count,attempt_max,pjsua_flag))
        try:
            #don't want to get stuck in here, so add a timer thread and run a process to kill chrome/chromedriver in another thread if we fail to start after N seconds
            t = threading.Timer(selenium_timeout, stop_selenium)
            t.start()
            retcode = start_jibri_selenium(url, token, chrome_binary_path=c_chrome_binary_path, google_account=c_google_account, google_account_password=c_google_account_password, xmpp_login=c_xmpp_login, xmpp_password=c_xmpp_password, boshdomain=boshdomain, displayname=c_display_name, email=c_email, pjsua_flag=pjsua_flag)
            try:
                t.cancel()
            except Exception as e:
                logging.info("Failed to cancel stop callback thread timer inside check_selenum_running: %s"%e)

            if retcode == 0:
                #ok so we launched the URL, now wait for XMPP connection to be established, download bitrate to be seen
                retcode = connect_confirm_bitrate_jibri_selenium()

        except Exception as e:
            #oops it all went awry, so try again?
            #quit chrome (should already be handled by above)
            logging.error("Jibri Startup exception during attempt %d/%d: %s"%(attempt_count,attempt_max,e))
            #make sure our retcode isn't 0, since we hit an exception here
            retcode=9999

        if retcode > 0:
            # We failed to start, bail.
            logging.info("start_jibri_selenium returned retcode=%s during attempt %d/%d"%(str(retcode),attempt_count,attempt_max))
        else:
            #success, so don't try again, and just move on
            logging.info("Selenium started successfully on attempt %d/%d"%(attempt_count,attempt_max))
            break

    if retcode > 0:
        #final failure handling
        logging.info("start_jibri_selenium returned retcode=" + str(retcode))
        jibri_stop_callback('startup_selenium_error')

    else:
        #we got a selenium, so start ffmpeg or pjsua
        if pjsua_flag:
            watcher_value="%s-|-%s"%(sipaddress,room_name)
            launch_err = launch_pjsua(sipaddress, room_name)
        else:
            watcher_value="%s-|-%s-|-%s-|-%s-|-%s-|-%s"%(recording_mode,url,recording_path,token,stream_id,backup)
            launch_err = launch_ffmpeg(url=url,recording_path=recording_path,token=token,stream_id=stream_id, backup=backup)


        if launch_err == True:
            #launch went smoothly, so now trigger the watcher thread
            update_jibri_status('started')
            queue_watcher_start(watcher_value)
            logging.info("queued job for jibri_watcher.")
        elif launch_err:
            #launch failed, return is the error code
            logging.error("startup failed: %s"%launch_err)
            jibri_stop_callback(launch_err)
        else:
            #launch went so poorly we didn't even return, error unknown
            logging.error("startup failed, unknown error")
            jibri_stop_callback('startup_failed')

    logging.info("jibri_start_callback completed...")



def wait_until_pjsua_running(attempts=3,interval=1):
    success = False
    try:
        #first try to start pjsua
        success = check_pjsua_running()
        attempt_count=0
        while not success:
            if attempt_count >= attempts:
                logging.warn("PJSUA failed to connect/run after %s checks"%attempt_count)
                success = False
                raise ValueError('PJSUA Failed to connect/run after %s checks'%attempt_count)
                break

            time.sleep(interval)
            success = check_pjsua_running()
            attempt_count=attempt_count+1
    except Exception as e:
        #oops it all went awry
        success = False
        logging.warn("Exception occured waiting for pjsua running: %s"%e)

    return success    

def launch_pjsua(sipaddress, displayname=''):
    global pjsua_failure_code
    #we only attempt to launch pjsua
    try:
        logging.info("Starting pjsua")
        retcode = start_pjsua(sipaddress, displayname)
        if retcode == 0:
            #make sure we wrote a pid, so that we can track this ffmpeg process
            pjsua_pid_file = "/var/run/jibri/pjsua.pid"
            try:
                with open(pjsua_pid_file) as f:
                    pjsua_pid = int(f.read().strip())
            except Exception as e:
                #oops it all went awry while starting up ffmpeg, something is seriously wrong so no retries
                #clean up pjsua and kill off any last pieces
                logging.error("start_pjsua had an exception %s"%e)
                return 'pjsua_startup_exception'

            #now that we have a pid, let's make sure pjsua is really connected
            logging.info("Waiting for pjsua connection")
            success = wait_until_pjsua_running()
            if success:
                logging.info("Successful pjsua startup")
                return True
            else:
                #we didn't start properly, so let's reset pjsua
                logging.error("wait_until_pjsua_running failed")
                kill_pjsua_process()
        else:
            #look up whatever the failure code was
            handle_pjsua_failure()
            if pjsua_failure_code:
                reason = 'pjsua_'+pjsua_failure_code
            else:
                reason = 'pjsua_startup_error'
            logging.error("start_pjsua returned retcode=%s reason=%s"%(str(retcode),reason))
            return reason

        #we made it all the way past the while loop and didn't return for either
        #a fatal error OR success, so apparently we just never started streaming
        logging.error("Failed to start pjsua client")
        return 'pjsua_startup_streaming_error'

    except Exception as e:
        #oops it all went awry
        success = False
        logging.warn("Exception occured launching pjsua: %s"%e)
        return 'pjsua_startup_streaming_exception'

def launch_ffmpeg(url,recording_path='',token='',stream_id='', backup=''):
    recording_file = recording_path + datetime.now().strftime('/%Y%m%d%H%M%S.mp4')
    #we will allow the following number of attempts:
    try:
        #first try to start ffmpeg
        attempt_count=0
        attempt_max=3
        while attempt_count<attempt_max:
            attempt_count=attempt_count+1
            logging.info("Starting ffmpeg attempt %d/%d"%(attempt_count,attempt_max))
            retcode = start_ffmpeg(url,recording_file,token,stream_id, backup)
            if retcode == 0:
                #make sure we wrote a pid, so that we can track this ffmpeg process
                try:
                    with open(ffmpeg_pid_file) as f:
                        ffmpeg_pid = int(f.read().strip())
                except Exception as e:
                    #oops it all went awry while starting up ffmpeg, something is seriously wrong so no retries
                    #clean up ffmpeg and kill off any last pieces
                    logging.error("start_ffmpeg had an exception %s"%e)
                    return 'ffmpeg_startup_exception'

                #now that we have a pid, let's make sure ffmpeg is really streaming
                success = wait_until_ffmpeg_running()
                if success:
                    #we are live!  Let's tell jicofo and start watching ffmpeg
                    logging.info("ffmpeg process started successfully")
                    return True
                else:
                    #we didn't start properly, so let's reset ffmpeg and try again
                    kill_ffmpeg_process()
            else:
                logging.error("start_ffmpeg returned retcode=" + str(retcode))
                return 'startup_ffmpeg_error'

        #we made it all the way past the while loop and didn't return for either
        #a fatal error OR success, so apparently we just never started streaming
        logging.error("Failed to start ffmpeg streaming 3 times in a row, out of attempts")
        return 'startup_ffmpeg_streaming_error'

    except Exception as e:
        #oops it all went awry
        success = False
        logging.warn("Exception occured waiting for ffmpeg running: %s"%e)
        return 'startup_ffmpeg_streaming_exception'


def wait_until_ffmpeg_running(attempts=15,interval=1):
    success = False
    try:
        #first try to start ffmpeg
        success = check_ffmpeg_running()
        attempt_count=0
        while not success:
            if attempt_count >= attempts:
                logging.warn("FFMPEG failed to start streaming after %s read attempts"%attempt_count)
                success = False
                raise ValueError('FFMPEG Failed to start streaming after %s read attempts'%attempt_count)
                break

            time.sleep(interval)
            success = check_ffmpeg_running()
            attempt_count=attempt_count+1
    except Exception as e:
        #oops it all went awry
        success = False
        logging.warn("Exception occured waiting for ffmpeg running: %s"%e)

    return success


def queue_watcher_start(msg):
    logging.info("queueing job for jibri_watcher")
    global watcher_queue
    watcher_queue.put(msg)

def start_jibri_selenium(url,token='token',chrome_binary_path=None,google_account=None,google_account_password=None, xmpp_login=None, xmpp_password=None, boshdomain=None, displayname=None, email=None, pjsua_flag=False):
    retcode=0
    global js

    token='abc'

    if pjsua_flag:
        #pjsua is enabled, so set the gateway flag
        url = "%s#config.iAmSipGateway=true&config.ignoreStartMuted=true"%(url)
    else:
        #only set the iAmRecorder flag if the pjsua functionality is not enabled
        url = "%s#config.iAmRecorder=true&config.externalConnectUrl=null"%(url)

    if boshdomain:
        logging.info('overriding config.hosts.domain with boshdomain: %s'%boshdomain)
        url = "%s&config.hosts.domain=\"%s\""%(url,boshdomain)

    logging.info(
        "starting jibri selenium, url=%s, google_account=%s, xmpp_login=%s" % (
            url, google_account, xmpp_login))

    js = JibriSeleniumDriver(url,token,binary_location=chrome_binary_path, google_account=google_account, google_account_password=google_account_password, displayname=displayname, email=email, xmpp_login=xmpp_login, xmpp_password=xmpp_password, pjsua_flag=pjsua_flag)

    if not check_selenium_audio_stream(js):
        logging.warn("jibri detected audio issues during startup, bailing out.")
        retcode=3
    else:
        js.launchUrl()
        if not js:
            logging.warn("jibri detected selenium driver went away, bailing out.")
            retcode=9999

    return retcode


def connect_confirm_bitrate_jibri_selenium():
    global js
    retcode = 9999
    if js.waitXMPPConnected():
      if js.waitDownloadBitrate()>0:
        #everything is AWESOME!
        retcode=0
      else:
        #didn't find any data flowing to JIBRI
        retcode=1336
    else:
      #didn't launch chrome properly right
      retcode=1337

    return retcode


def check_selenium_audio_stream(js, audio_url=None, audio_delay=1):
    #first send the selenium driver to something with audio
    if not audio_url:
        audio_url = default_audio_url

    js.driver.get(audio_url)
    #wait a bit to be sure audio is flow
    time.sleep(audio_delay)

    #now execute audio check script
    ret = call([check_audio_script])
    if ret != 0:
        logging.warn("ERROR: failed audio check, no audio levels detected: %s"%ret)
        return False
    else:
        logging.info("Audio levels confirmed OK.")
        return True

def start_pjsua(sipaddress, displayname=''):
    logging.info("starting jibri pjsua with sip address=%s displayname=%s" % (sipaddress,displayname))
    return call([launch_gateway_script, sipaddress, displayname],
             shell=False)


def start_ffmpeg(url='ignore', recording_path='ignore', token='ignore', stream_id='ignore', backup=''):
    logging.info("starting jibri ffmpeg with youtube-stream-id=%s" % stream_id)
    return call([launch_recording_script, url, recording_path, token, stream_id, backup],
             shell=False)

def jibri_stop_callback(status=None):
    global current_environment
    global active_client
    logging.info("jibri_stop_callback run with status %s"%status)
    if active_client and not status == 'xmpp_stop':
        #the stop wasn't specifically requested, so report this error to jicofo
        status = 'error|'+status
        logging.info("queueing error %s for host %s"%(status,active_client.hostname))
        queues[active_client.hostname].put(status)

    #no longer report ourselves as recording in the last environment, clear our active client
    current_environment = ''
    active_client = None
    reset_recording()
    finalize_recording()
    release_recording()
    #report ourselves idle
    update_jibri_status('idle')

#c is client to NOT send updates to, used for 'busy' case
def update_jibri_status(status, c=None):
    logging.info("update_jibri_status")
    global queues
    for hostname in queues:
        logging.info("looping through queue for host %s"%hostname)
        if not c or c.hostname != hostname:
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
    global pjsua_flag
    global pjsua_failure_code

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
            #save the parameter for use in retry logic
            retry_value = msg

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

            if pjsua_flag:
                #during ongoing check, ensure that pjsua process is running
                result = check_pjsua_running()
            else:
                #during ongoing check, ensure that ffmpeg process is running but don't search for frame=
                result = check_ffmpeg_running(False)

                if not result:
                    logging.error("ffmpeg process no longer running, attempting a retry")
                    #a negative result here means that ffmpeg WAS running fine and has now died
                    #try to restart it before returning control back to jicofo
                    result = retry_ffmpeg(retry_value)
                    if result:
                        logging.info("ffmpeg process restarted succcessfully")
                    else:
                        logging.error("ffmpeg retry process failed")


            selenium_result = check_selenium_running()

            if not selenium_result:
                logging.info("Received a failure checking if selenium is running, checking again in 1 second...")
                time.sleep(1)
                #try at least 2 more times
                selenium_result = check_selenium_running()
                if not selenium_result:
                    logging.info("Received a second failure checking if selenium is running, checking again in 1 second...")
                    time.sleep(1)
                    selenium_result = check_selenium_running()

            if result and selenium_result:
                logging.debug("ffmpeg/pjsua and selenium still running, sleeping...")
                time.sleep(5)
            else:                
                logging.info("ffmpeg/pjsua or selenium no longer running, triggering callback")
                if not result:
                    if pjsua_flag:
                        if pjsua_failure_code:
                            reason = 'pjsua_'+pjsua_failure_code
                        else:
                            reason = 'pjsua_died'
                    else:
                        reason = 'ffmpeg_died'
                if not selenium_result:
                    if selenium_result == None:
                        reason = 'selenium_hangup'
                    else:
                        reason = 'selenium_died'
                loop.call_soon_threadsafe(finished_callback, reason)
        logging.info("jibri_watcher finished loop...")

def retry_ffmpeg(retry_value):
    result = False
    retry_values = retry_value.split('-|-')
    url=''
    recording_path=''
    token=''
    stream_id=''
    backup=''
    if len(retry_values)>1:
        recording_mode=retry_values[0]
        url=retry_values[1]
        recording_path=retry_values[2]
        token=retry_values[3]
        stream_id=retry_values[4]
        backup=retry_values[5]
    else:
        stream_id=retry_values[0]
        backup=''

    launch_err = launch_ffmpeg(url,recording_path,token,stream_id,backup)
    if launch_err == True:
        #a restart of ffmpeg was successful, so lets do our running check and move on
        result = check_ffmpeg_running(False)
    else:
        #ffmpeg failed to start after dying on us, so result stays false
        result = False

    return result


#utility function called by jibri_watcher, checks for the selenium process, returns true if the driver object is defined and shows connected to selenium
def check_selenium_running():
    global js
    selenium_timeout=10
    if not js:
        return False
    else:
        #first start a thread to ensure we stop everything if needed
        t = threading.Timer(selenium_timeout, jibri_stop_callback, kwargs=dict(status='selenium_stuck'))
        t.start()
        running= js.checkRunning()
        try:
            t.cancel()
        except Exception as e:
            logging.info("Failed to cancel stop callback thread timer inside check_selenum_running: %s"%e)
        return running


#utility function called by jibri_watcher, checks for the ffmpeg process, returns true if the pidfile can be found and the process exists
def check_ffmpeg_running(include_frame_check=True):
    try:
        with open(ffmpeg_pid_file) as f:
            ffmpeg_pid = int(f.read().strip())
    except Exception:
        #oops it all went awry
        #quit chrome
        #clean up ffmpeg and kill off any last pieces
        return None

    try:
        #check that ffmpeg is running
        os.kill(ffmpeg_pid, 0)
        if include_frame_check:
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

#utility function called by jibri_watcher, checks for the pjsua process, returns true if the pidfile can be found and the process exists
def check_pjsua_running():
    global pjsua_failure_code
    #clear the global failure code on each check of pjsua (assumes last check passed, otherwise this function wouldn't have been called)
    pjsua_failure_code = ''
    try:
        with open(pjsua_pid_file) as f:
            pjsua_pid = int(f.read().strip())
    except Exception:
        #oops no pid found, so stand down
        return None

    try:
        #check that pjsua is running
        os.kill(pjsua_pid, 0)

#NO OUTPUT CHECK FOR PJSUA YET
#@TODO: FIND OUTPUT THAT INDICATES PJSUA IS WORKING/BROKEN
        #nothing is wrong, so wait a bit
        return True
    except:
        #oops it all went awry
        return handle_pjsua_failure()

def handle_pjsua_failure():
    global pjsua_failure_code
    #pjsua WAS running and now it's failed.  Check for failure file, and maybe restart it
    pjsua_failure = -1

    #read the results file if it exists and is readable
    try:    
        with open(pjsua_result_file) as f:
            pjsua_failure = int(f.read().strip())
    except Exception:
        #oops no result found, so stand down
        return False

    #if no pjsua failure loaded, something really bad went wrong
    if pjsua_failure == -1:
        #we should never hit this point
        pjsua_failure_code = 'unknown'
        return False

    #0 means a clean return (hangup), so no need to retry
    if pjsua_failure == 0:
        pjsua_failure_code = 'hangup'
        return False

    #486 is busy/call rejected, so no need to retry
    if pjsua_failure == 2:
        pjsua_failure_code = 'busy'
        return False

    #default to failing
    return False

#function to start sleekxmpp from within its own thread
def start_sleekxmpp(hostname, loop, recording_lock, signal_queue,port=5222):
    global clients
    global client_opts
    connect_opts = client_opts[hostname]

    logging.info("Creating a client for hostname: %s with options: %s" %(hostname,str(connect_opts)))
    c = JibriXMPPClient(
        hostname,
        connect_opts['jid'], connect_opts['password'], connect_opts['room'], connect_opts['nick'],
        roompass=connect_opts['roompass'],
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
    logging.debug("Connecting client for hostname: %s port %d" %(hostname,port))
    if c.connect((hostname, port)):
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
    finalize_recording()
    release_recording()

    success = True
    result = {'success': success}
    return jsonify(result)

#call to begin recording
@app.route('/jibri/api/v1.0/sipstart', methods=['POST','GET'])
def url_start_gateway():
    global rest_token
    if request.method == 'POST':
        url = request.json['url']
        sipaddress = request.json['sipaddress']
        token = request.json['token']
        displayname = request.json['displayname']
        room = request.json['room']
    else:
        url = request.args['url']
        sipaddress = request.args['sipaddress']
        token = request.args['token']
        displayname = request.args['displayname']
        room = request.args['room']
    if not url or not sipaddress:
        result = {'success': False, 'error':'Bad Parameters', 'request':request}
        return jsonify(result)
    else:
        if rest_token == token:
            global recording_lock
            if recording_lock.acquire(False):
                retcode=jibri_start_callback(None, url, '', sipaddress=sipaddress, displayname=displayname,room=room)
                if retcode == 0:
                    result = {'success': success, 'url':url, 'sipaddress':sipaddress, 'token':token, 'displayname':displayname}
                else:
                    success = False
                    result = {'success': success, 'jibriseleniumerror':True, 'url':url, 'sipaddress':sipaddress, 'token':token, 'displayname':displayname}
            else:
                success = False
                result = {'success': success, 'error': 'Already recording'}                    
        else:
            success = False
            result = {'success': success, 'error': 'Token does not match'}
        return jsonify(result)

#TODO: make this actually check something?
@app.route('/jibri/health', methods=['GET'])
def url_health_check():
    global recording_lock
    global health_lock
    global current_environment

    #put an item on the XMPP queue, and watch for a return by callback
    result={'recording':recording_lock.locked(), 'health':False, 'XMPPConnected':False, 'environment':current_environment}

    result['jibri_xmpp']=check_xmpp_running()

    #only return good if we are connected via jibri, and not recording OR recording with good selenium health
    if result['jibri_xmpp']:
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

    optp.add_option("-c", "--config", dest="config", help="Server config file to use, JSON format", default=os.getcwd() + "/../config.json")

    optp.add_option("-j", "--jid", dest="jid", help="JID to use")
    optp.add_option("-u", "--url", dest="url", help="URL to record, with token %ROOM% for room name: https://meet.jit.si/%ROOM%")
    optp.add_option("-p", "--password", dest="password", help="password to use")
    optp.add_option("-r", "--room", dest="room", help="MUC room to join")
    optp.add_option("", "--room-name", dest="roomname", help="MUC room name to join (combined with MUC server if room isn't provided)")
    optp.add_option("", "--muc-server-prefix", dest="mucserverprefix", help="MUC server prefix to search fro subdomains (combined with xmpp domain and room name if room isn't provided)")
    optp.add_option("", "--jid-server-prefix", dest="jidserverprefix", help="JID server prefix to auth (combined with xmpp domain and username if room isn't provided)")
    optp.add_option("", "--bosh-domain-prefix", dest="boshdomainprefix", help="BOSH domain prefix to auth (combined with xmpp domain if boshdomain isn't provided)")
    optp.add_option("", "--brewery-prefix", dest="breweryprefix", help="MUC server prefix to join (combined with xmpp domain and room name if room isn't provided)")
    optp.add_option("", "--bosh-domain", dest="boshdomain", help="BOSH domain to override default for site")
    optp.add_option("", "--jid-username", dest="jidusername", help="JID user to auth (combined with xmpp domain and username if room isn't provided)")
    optp.add_option("-x", "--xmpp-domain", dest="xmppdomain", help="XMPP domain, used to generate other parameters")
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

    optp.add_option("-a", "--google-account", dest="google_account", help="Login for google",
                    default=None)

    optp.add_option("-g", "--google-account-password", dest="google_account_password", help="Password for google",
                    default=None)


    optp.usage = 'Usage: %prog [options] <server_hostname1 server_hostname2 ...>'

    global opts
    opts, args = optp.parse_args()

    # Setup logging.
    logging.basicConfig(level=opts.loglevel,
                        format='%(asctime)s %(levelname)-8s %(message)s')

    #now parse and handle configuration params from the file, and build client config
    default_client_opts = {
        'jid_username':'jibri',
        'jidserver_prefix':'',
        'mucserver_prefix':'conference.',
        'boshdomain_prefix':'',
        'selenium_xmpp_prefix':'',
        'boshdomain':'',
        'roompass':'',
        'nick':'jibri',
        'usage_timeout': 0,
        'recording_directory':'./recordings'
    }
    default_servers = []
    client_opts = {}
    config_environments = {}

    if opts.config:
        config_data=None
        try:
            with open(opts.config) as data_file:
                config_data = json.load(data_file)
        except FileNotFoundError:
            logging.warn('Configuration file %s not found.'%opts.config)
        except PermissionError:
            logging.warn('Configuration file %s access denied.'%opts.config)
        except Exception as e:
            logging.error('Exception while reading JSON configuration %s'%e)

        if config_data:
            #first we read basic global config parameters
            if 'jid' in config_data:
                default_client_opts['jid'] = config_data['jid']

            #XMPP password for the jibri user JID
            if 'password' in config_data:
                default_client_opts['password'] = config_data['password']

            #nickname for this jibri
            if 'nick' in config_data:
                default_client_opts['nick'] = config_data['nick']

            #room for jibri to join
            if 'room' in config_data:
                default_client_opts['room'] = config_data['room']

            #password on room for jibri to join
            if 'roompass' in config_data:
                default_client_opts['roompass'] = config_data['roompass']

            #main XMPP domain value
            if 'xmpp_domain' in config_data:
                default_client_opts['xmpp_domain'] = config_data['xmpp_domain']

            #get a URL template to visit when launched
            #can include %ROOM% which is replaced by the requested room at launch time
            if 'url' in config_data:
                default_client_opts['url'] = config_data['url']

            #timeout when communication with external components
            if 'usage_timeout' in config_data:
                default_client_opts['usage_timeout'] = config_data['usage_timeout']

            #token for accessing JIBRI via REST
            if 'resttoken' in config_data:
                default_client_opts['resttoken'] = config_data['resttoken']

            #path to chrome binary
            if 'chrome_binary_path' in config_data:
                default_client_opts['chrome_binary_path'] = config_data['chrome_binary_path']

            #switch to use pjsua instead of ffmpeg
            if 'pjsua_flag' in config_data:
                pjsua_flag = config_data['pjsua_flag']

            #google account to log in to for selenium chrome session
            if 'google_account' in config_data:
                default_client_opts['google_account'] = config_data['google_account']

            #google password to log in to for selenium chrome session
            if 'google_account_password' in config_data:
                default_client_opts['google_account_password'] = config_data['google_account_password']

            #login for selenium XMPP meet-jitsi user
            if 'selenium_xmpp_login' in config_data:
                default_client_opts['selenium_xmpp_login'] = config_data['selenium_xmpp_login']

            #password for selenium XMPP meet-jitsi user
            if 'selenium_xmpp_password' in config_data:
                default_client_opts['selenium_xmpp_password'] = config_data['selenium_xmpp_password']

            #start of host part for selenium XMPP meet-jitsi user
            if 'selenium_xmpp_prefix' in config_data:
                default_client_opts['selenium_xmpp_prefix'] = config_data['selenium_xmpp_prefix']

            #user part for selenium XMPP meet-jitsi user
            if 'selenium_xmpp_username' in config_data:
                default_client_opts['selenium_xmpp_username'] = config_data['selenium_xmpp_username']


            #user part of JID
            if 'jid_username' in config_data:
                default_client_opts['jid_username'] = config_data['jid_username']

            #start of host part of JID
            if 'jidserver_prefix' in config_data:
                default_client_opts['jidserver_prefix'] = config_data['jidserver_prefix']

            #start of host part of muc server
            if 'mucserver_prefix' in config_data:
                default_client_opts['mucserver_prefix'] = config_data['mucserver_prefix']

            #start of host part of muc server
            if 'brewery_prefix' in config_data:
                default_client_opts['brewery_prefix'] = config_data['brewery_prefix']

            #start of host part of bosh server
            if 'boshdomain_prefix' in config_data:
                default_client_opts['boshdomain_prefix'] = config_data['boshdomain_prefix']

            #start of host part of bosh server
            if 'boshdomain' in config_data:
                default_client_opts['boshdomain'] = config_data['boshdomain']

            #name part of room for jibri to join
            if 'roomname' in config_data:
                default_client_opts['roomname'] = config_data['roomname']

            #default display name for jibri selenium session
            if 'displayname' in config_data:
                default_display_name = config_data['displayname']

            #default display name for jibri selenium session
            if 'email' in config_data:
                default_email = config_data['email']

            if 'servers' in config_data:
                for hostname in config_data['servers']:
                    default_servers.append(hostname)

            if 'environments' in config_data:
                config_environments = config_data['environments']

        else:
            logging.warn('No data found in config file %s'%opts.config)



    #environment used if command line not provided
    if not opts.jid:
        if not os.environ.get('JID') is None:
          opts.jid = os.environ.get('JID')

    if not opts.url:
        if not os.environ.get('URL') is None:
          opts.url = os.environ.get('URL')

    if not opts.room:
        if not os.environ.get('ROOM') is None:
          opts.room = os.environ.get('ROOM')

    if not opts.password:
        if not os.environ.get('PASS') is None:
          opts.password = os.environ.get('PASS')

    if not opts.roompass:
        if not os.environ.get('ROOMPASS') is None:
          opts.roompass = os.environ.get('ROOMPASS')

    if not opts.roomname:
        if not os.environ.get('ROOMNAME') is None:
          opts.roomname = os.environ.get('ROOMNAME')

    if not opts.xmppdomain:
        if not os.environ.get('XMPP_DOMAIN') is None:
          opts.xmppdomain = os.environ.get('XMPP_DOMAIN')

    if os.environ.get('TIMEOUT') is not None:
      opts.timeout = os.environ.get('TIMEOUT')

    if os.environ.get('REST_TOKEN') is not None:
      opts.rest_token = os.environ.get('REST_TOKEN')

    if not opts.chrome_binary_path:
        if os.environ.get('CHROME_BINARY') is not None:
          opts.chrome_binary_path = os.environ.get('CHROME_BINARY')

    if os.environ.get('GOOGLE_ACCOUNT') is not None:
      opts.google_account = os.environ.get('GOOGLE_ACCOUNT')

    if os.environ.get('GOOGLE_ACCOUNT_PASSWORD') is not None:
      opts.google_account_password = os.environ.get('GOOGLE_ACCOUNT_PASSWORD')


    #pull the servers from the environment if not specified on the command line
    if not args:
        if not os.environ.get('SERVERS') is None:
          args = os.environ.get('SERVERS').split(" ")

    #no matter their source, append the servers to the default list
    if args:
        for hostname in args:
            default_servers.append(hostname)

    #now override file parameters with command line if specified
    if opts.room:
        default_client_opts['room'] = opts.room
    if opts.url:
        default_client_opts['url'] = opts.url
    if opts.jid:
        default_client_opts['jid'] = opts.jid
    if opts.password:
        default_client_opts['password'] = opts.password
    if opts.nick:
        default_client_opts['nick'] = opts.nick

    if opts.xmppdomain:
        default_client_opts['xmpp_domain'] = opts.xmppdomain
    if opts.roomname:
        default_client_opts['roomname'] = opts.roomname
    if opts.jidusername:
        default_client_opts['jid_username'] = opts.jidusername
    if opts.jidserverprefix:
        default_client_opts['jidserver_prefix'] = opts.jidserverprefix
    if opts.mucserverprefix:
        default_client_opts['mucserver_prefix'] = opts.mucserverprefix
    if opts.breweryprefix:
        default_client_opts['brewery_prefix'] = opts.breweryprefix
    if opts.boshdomainprefix:
        default_client_opts['boshdomain_prefix'] = opts.boshdomainprefix
    if opts.boshdomain:
        default_client_opts['boshdomain'] = opts.boshdomain
    if opts.chrome_binary_path:
        default_client_opts['chrome_binary_path'] = opts.chrome_binary_path
    if opts.google_account:
        default_client_opts['google_account'] = opts.google_account
    if opts.google_account_password:
        default_client_opts['google_account_password'] = opts.google_account_password
    if opts.timeout is not None:
        default_client_opts['usage_timeout'] = opts.timeout

    if not 'brewery_prefix' in default_client_opts or not default_client_opts['brewery_prefix']:
        default_client_opts['brewery_prefix'] = default_client_opts['mucserver_prefix']
    #finally build up server configurations from all the above pieces
    #first walk through the default servers specified in config or command line
    for hostname in default_servers:
        client_opts[hostname] = default_client_opts.copy()

    #now loop through the environments and pull out the servers
    for item in config_environments.keys():
        if 'servers' not in config_environments[item]:
            logging.warn('Environment config item %s missing serverlist, incomplete, skipping...'%item)
            continue

        #build a shared config for all clients sharing these parameters
        client_config = default_client_opts.copy()
        if 'environment' not in client_config:
            client_config['environment'] = item
        for key in config_environments[item]:
            if key == 'servers':
                #skip, don't need to save all servers to client config for now
                pass
            else:
                client_config[key] = config_environments[item][key]

        #now save those servers into global client options
        for hostname in config_environments[item]['servers']:
            client_opts[hostname] = client_config


    #final sanity check of configuration parameters
    candidate_hosts = list(client_opts.keys())
    for hostname in candidate_hosts:
        if not 'jid' in client_opts[hostname]:
            if 'jid_username' in client_opts[hostname] and 'jidserver_prefix' in client_opts[hostname] and 'xmpp_domain' in client_opts[hostname]:
                client_opts[hostname]['jid'] = '%s@%s%s'%(client_opts[hostname]['jid_username'],client_opts[hostname]['jidserver_prefix'],client_opts[hostname]['xmpp_domain'])
            else:
                logging.warn('No JID specified in client option, removing from list: %s'%client_opts[hostname])
                del client_opts[hostname]
                continue

        if not 'url' in client_opts[hostname]:
            if 'xmpp_domain' in client_opts[hostname]:
                client_opts[hostname]['url'] = 'https://%s/%%SUBDOMAIN%%%%ROOM%%'%client_opts[hostname]['xmpp_domain']
            else:
                logging.warn('No URL specified in client option, removing from list: %s'%client_opts[hostname])
                del client_opts[hostname]
                continue

        if not 'room' in client_opts[hostname]:
            if 'roomname' in client_opts[hostname] and 'brewery_prefix' in client_opts[hostname] and 'xmpp_domain' in client_opts[hostname]:
                client_opts[hostname]['room'] = '%s@%s%s'%(client_opts[hostname]['roomname'],client_opts[hostname]['brewery_prefix'],client_opts[hostname]['xmpp_domain'])
            else:
                logging.warn('No ROOM specified in client option, removing from list: %s'%client_opts[hostname])
                del client_opts[hostname]
                continue

        if not 'password' in client_opts[hostname]:
            logging.warn('No Password specified in client option, removing from list: %s'%client_opts[hostname])
            del client_opts[hostname]
            continue

        if not 'selenium_xmpp_login' in client_opts[hostname]:
            if 'selenium_xmpp_username' in client_opts[hostname] and 'xmpp_domain' in client_opts[hostname]:
                #build selenium username from xmpp domain plus prefix
                client_opts[hostname]['selenium_xmpp_login'] = '%s@%s%s'%(client_opts[hostname]['selenium_xmpp_username'],client_opts[hostname]['selenium_xmpp_prefix'],client_opts[hostname]['xmpp_domain'])

        if not 'boshdomain' in client_opts[hostname] or client_opts[hostname]['boshdomain'] == '':
            if 'boshdomain_prefix' in client_opts[hostname] and client_opts[hostname]['boshdomain_prefix'] and 'xmpp_domain' in client_opts[hostname] and client_opts[hostname]['xmpp_domain']:
                client_opts[hostname]['boshdomain'] = '%s%s'%(client_opts[hostname]['boshdomain_prefix'],client_opts[hostname]['xmpp_domain'])


    global rest_token
    rest_token = opts.rest_token

    if 'google_account' in default_client_opts:
        google_account = default_client_opts['google_account']

    if 'google_account_password' in default_client_opts:
        google_account_password = default_client_opts['google_account_password']

    if 'chrome_binary_path' in default_client_opts:
        chrome_binary_path = default_client_opts['chrome_binary_path']
        logging.info("Overriding chrome binary with value: %s"%chrome_binary_path)

    if 'selenium_xmpp_login' in default_client_opts:
        selenium_xmpp_login = default_client_opts['selenium_xmpp_login']

    if 'selenium_xmpp_password' in default_client_opts:
        selenium_xmpp_password = default_client_opts['selenium_xmpp_password']

    #handle SIGHUP graceful shutdown
    loop.add_signal_handler(signal.SIGHUP,sighup_handler, loop)

    #handle SIGTERM as immediate shutdown
    loop.add_signal_handler(signal.SIGTERM,sigterm_handler, loop)

    #debugging signal
    loop.add_signal_handler(signal.SIGUSR1,sigusr1_handler, loop)

# no longer debug
#    loop.set_debug(True)

    watcher_queue = Queue()

    logging.debug('Client Options: %s'%str(client_opts))
    if len(client_opts) == 0:
        logging.warn('No XMPP client options available, acting in pure REST mode')
    for hostname in client_opts:
        logging.debug('Starting up client thread for host: %s'%hostname)
        #make a queue for talking to the XMPP client thread
        queues[hostname] = Queue()
        loop.run_in_executor(None, start_sleekxmpp, hostname, loop, recording_lock, queues[hostname])

    #now start flask
    loop.run_in_executor(None, functools.partial(app.run, host='0.0.0.0'))
    loop.run_in_executor(None, start_jibri_watcher, watcher_queue, loop, jibri_stop_callback, default_client_opts['usage_timeout'])
    loop.run_forever()
    loop.close()
