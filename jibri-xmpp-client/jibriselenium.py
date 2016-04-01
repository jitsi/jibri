#!/usr/bin/env python3
import sys, getopt, os
import signal
import time
import pprint
import logging
from selenium import webdriver
from selenium.webdriver.common.action_chains import ActionChains
from selenium.common.exceptions import WebDriverException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait # available since 2.4.0
from selenium.webdriver.support import expected_conditions as EC # available since 2.26.0


class JibriSeleniumDriver():
    def __init__(self, url, authtoken=None, xmpp_connect_timeout=60, binary_location=None):

      #init based on url and optional token
      self.url = url
      self.authtoken = authtoken

      self.flag_jibri_identifiers_set = False

      #only wait this only before failing to load meet
      self.xmpp_connect_timeout = xmpp_connect_timeout

      self.desired_capabilities = DesiredCapabilities.CHROME
      self.desired_capabilities['loggingPrefs'] = { 'browser':'ALL' }

      self.options = Options()
      self.options.add_argument('--use-fake-ui-for-media-stream')
      self.options.add_argument('--start-maximized')
      self.options.add_argument('--kiosk')
      self.options.add_argument('--enabled')
      self.options.add_argument('--enable-logging')
      self.options.add_argument('--vmodule=*=3')
#      self.options.add_argument('--alsa-output-device=hw:0,1')
      if binary_location:
        self.options.binary_location = binary_location
      self.initDriver()

    def initDriver(self, options=None, desired_capabilities=None):
      #make sure the environment is set properly
      display=os.environ.get('DISPLAY', None)
      if not display:
        os.environ['DISPLAY'] =':0'

      if not desired_capabilities:
        desired_capabilities = self.desired_capabilities
      if not options:
        options = self.options

      print("Initializing Driver")
      self.driver = webdriver.Chrome(chrome_options=options, desired_capabilities=desired_capabilities)

    def setJibriIdentifiers(self):
      print("Setting jibri identifiers")
      self.execute_script("window.localStorage.setItem('displayname','Live Stream'); window.localStorage.setItem('email','recorder@jitsi.org');")
#      self.execute_script("window.localStorage.setItem('email','recorder@jitsi.org');")

    def launchUrl(self, url=None):
      if not url:
        url = self.url

      print("Launching URL: %s"%url)
      #we do this twice: once to launch the page the first time,
      if not self.flag_jibri_identifiers_set:
        self.driver.get(url)
        self.setJibriIdentifiers()
        self.flag_jibri_identifiers_set = True

      self.driver.get(url)

    def execute_async_script(self, script):
      try:
        response=self.driver.execute_script(script)
      except WebDriverException as e:
        pprint.pprint(e)
        response = None
#      except ConnectionRefusedError as e:
        #should kill chrome and restart? unhealthy for sure
#        pprint.pprint(e)
#        response = None
      except:
        print("Unexpected error:%s"%sys.exc_info()[0])
        raise

      return response


    def execute_script(self, script):
      try:
        response=self.driver.execute_script(script)
      except WebDriverException as e:
        pprint.pprint(e)
        response = None
#      except ConnectionRefusedError as e:
        #should kill chrome and restart? unhealthy for sure
#        pprint.pprint(e)
#        response = None
      except:
        print("Unexpected error:%s"%sys.exc_info()[0])
        raise

      return response

    def isXMPPConnected(self):
      response=''
      response = self.execute_async_script('return APP.conference._room.xmpp.connection.connected;')
      
      print('isXMPPConnected:%s'%response)
      return response
    
    def getDownloadBitrate(self):
      try:
        stats = self.execute_async_script("return APP.conference.getStats();")
        if stats == None:
          return 0
        return stats['bitrate']['download']
      except:
        return 0

    def waitDownloadBitrate(self,timeout=None, interval=5):
      logging.info('starting to wait for DownloadBitrate')
      if not timeout:
        timeout = self.xmpp_connect_timeout
      if self.getDownloadBitrate() > 0:
        logging.info('downloadbitrate > 0, done waiting')
        return True
      else:
        wait_time=0
        while wait_time < timeout:
          logging.info('waiting +%d = %d < %d for DownloadBitrate'%(interval, wait_time, timeout))
          time.sleep(interval)
          wait_time = wait_time + interval
          if self.getDownloadBitrate() > 0:
            logging.info('downloadbitrate > 0, done waiting')
            return True
          if wait_time >= timeout:
            logging.info('Timed out waiting for download bitrate')
            return False

    def waitXMPPConnected(self,timeout=None, interval=5):
      logging.info('starting to wait for XMPPConnected')
      if not timeout:
        timeout = self.xmpp_connect_timeout
      if self.isXMPPConnected():
        return True
      else:
        wait_time=0
        while wait_time < timeout:
          logging.info('waiting +%d = %d < %d for XMPPConnected'%(interval, wait_time, timeout))
          time.sleep(interval)
          wait_time = wait_time + interval
          if self.waitXMPPConnected():
            logging.info('XMPP connected, done waiting')
            return True
          if wait_time >= timeout:
            logging.info('Timed out waiting for XMPP connection')
            return False


    def checkRunning(self, timeout=2, download_timeout=5, download_interval=1):
      logging.debug('checkRunning selenium')
#      self.driver.set_script_timeout(10)
      try:
        element = WebDriverWait(self.driver, timeout).until(EC.presence_of_element_located((By.TAG_NAME, "body")))
        if element:
          return self.waitDownloadBitrate(timeout=download_timeout, interval=download_interval)
        else:
          return False
      except Exception as e:
        logging.info("Failed to run script properly: %s"%e)
#        raise e
      return False

    def quit(self):
      try:
        response=self.execute_script('return APP.conference._room.connection.disconnect();')
        #give chrome a chance to finish logging out
        time.sleep(2)
      except Exception as e:
        print("Failed to click hangup button")
        pprint.pprint(e)

      self.driver.quit()      


def sigterm_handler(a, b):
    if driver:
        driver.quit()
    exit("Jibri begone!")


if __name__ == '__main__':
  app = sys.argv[0]
  argv=sys.argv[1:]
  URL = ''
  token = ''
  loglevel='DEBUG'

  try:
    opts, args = getopt.getopt(argv,"hu:t:d:",["meeting_url=","token=","loglevel="])
  except getopt.GetoptError:
    print(app+' -u <meetingurl> -t <authtoken>')
    sys.exit(2)
  for opt, arg in opts:
    if opt == '-h':
       print(app+' -u <meetingurl> -t <authtoken>')
       sys.exit()
    elif opt in ("-u", "--meeting_url"):
       URL = arg
    elif opt in ("-t", "--token"):
       token = arg
    elif opt in ("-d", "--debug"):
       loglevel = arg

  if not URL:
      print('No meeting URL provided.')
      exit(1)

  logging.basicConfig(level=loglevel,
                        format='%(asctime)s %(levelname)-8s %(message)s')
  signal.signal(signal.SIGTERM, sigterm_handler)
  js = JibriSeleniumDriver(URL,token)
  js.launchUrl()
  if js.checkRunning(download_timeout=60):
    if js.waitXMPPConnected():
      print('Successful connection to meet')
      if js.waitDownloadBitrate()>0:
        print('Successfully receving data in meet, waiting 60 seconds for fun')
        interval=1
        timeout=60
        sleep_clock=0
        while sleep_clock < timeout:
          br=js.getDownloadBitrate()
          if not br:
            print('No more data, waiting a few...')
            if not js.waitDownloadBitrate():
              print('Never got more data, finishing up...')
              break
          else:
            print('Current bitrate: %s'%br)

          sleep_clock=sleep_clock+interval
          time.sleep(interval)

      else:
        print("Failed to receive data in meet client")

    else:
      print("Failed to connect to meet")
  else:
    print("Failed to start selenium/launch URL")

  print("Done waiting, finishing up and exiting...")
  js.quit()
