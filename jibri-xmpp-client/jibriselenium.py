#!/usr/bin/env python3
import sys, getopt, os
import signal
import time
import pprint
from selenium import webdriver
from selenium.common.exceptions import WebDriverException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities

class JibriSeleniumDriver():
    def __init__(self, url, authtoken=None, xmpp_connect_timeout=60):

      #init based on url and optional token
      self.url = url
      self.authtoken = authtoken

      #only wait this only before failing to load meet
      self.xmpp_connect_timeout = xmpp_connect_timeout

      self.desired_capabilities = DesiredCapabilities.CHROME
      self.desired_capabilities['loggingPrefs'] = { 'browser':'ALL' }

      self.options = Options()
      self.options.add_argument('--use-fake-ui-for-media-stream')
      self.options.add_argument('--start-maximized')
      self.options.add_argument('--kiosk')
      self.options.add_argument('--enable-logging')
      self.options.add_argument('--vmodule=*=3')
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

    def launchUrl(self, url=None):
      if not url:
        url = self.url

      print("Launching URL: %s"%url)
      self.driver.get(url)

    def isXMPPConnected(self):
      response=''
      try:
        response = self.driver.execute_script('return APP.conference._room.xmpp.connection.connected;')
      except WebDriverException as e:
        pprint.pprint(e)
        response = None
      except:
        print("Unexpected error:%s"%sys.exc_info()[0])
        raise
      
      print('isXMPPConnected:%s'%response)
      return response
    
    def getDownloadBitrate(self):
      try:
        stats = self.driver.execute_script("return APP.conference.getStats();")
        return stats['bitrate']['download']
      except:
        return 0


    def waitDownloadBitrate(self,timeout=None, interval=5):
      if not timeout:
        timeout = self.xmpp_connect_timeout
      if self.getDownloadBitrate() > 0:
        return True
      else:
        wait_time=0
        while wait_time < timeout:
          time.sleep(interval)
          wait_time = wait_time + interval
          if self.getDownloadBitrate() > 0:
            return True
          if wait_time >= timeout:
            return False

    def waitXMPPConnected(self,timeout=None, interval=5):
      if not timeout:
        timeout = self.xmpp_connect_timeout
      if self.isXMPPConnected():
        return True
      else:
        wait_time=0
        while wait_time < timeout:
          time.sleep(interval)
          wait_time = wait_time + interval
          if self.waitXMPPConnected():
            return True
          if wait_time >= timeout:
            return False


    def quit(self):
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
  try:
    opts, args = getopt.getopt(argv,"hu:t:",["meeting_url=","token="])
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

  if not URL:
      print('No meeting URL provided.')
      exit(1)

  signal.signal(signal.SIGTERM, sigterm_handler)
  js = JibriSeleniumDriver(URL,token)
  js.launchUrl()
  if js.waitXMPPConnected():
    print('Successful connection to meet')
    if js.waitDownloadBitrate()>0:
      print('Successfully receving data in meet, waiting 60 seconds for fun')
      time.sleep(60)
    else:
      print("Failed to receive data in meet client")

  else:
    print("Failed to connect to meet")

  js.quit()




#driver.execute_script("window.localStorage.displayname = 'JIBRI'")
#driver.execute_script("window.localStorage.email = 'jibri@mustelinae.net'")

#driver.refresh()
#time.sleep(1)
#driver.find_element_by_id("toolbar_button_camera").click()
#driver.find_element_by_id("toolbar_button_mute").click()

#driver.execute_script("window.localStorage.displayname='Recorder'")
#driver.find_element_by_id("toolbar_button_chat").click()

#driver.quit()


