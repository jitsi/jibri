#!/usr/bin/env python3
import sys, getopt
import signal
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities

app = sys.argv[0]
argv=sys.argv[1:]

d = DesiredCapabilities.CHROME
d['loggingPrefs'] = { 'browser':'ALL' }

def sigterm_handler(a, b):
    if driver:
        driver.quit()
    exit("Jibri begone!")
signal.signal(signal.SIGTERM, sigterm_handler)

options = Options()
options.add_argument('--use-fake-ui-for-media-stream')
#options.add_argument('--use-fake-device-for-media-stream')
#options.add_argument('--use-file-for-fake-video-capture=/home/boris/720p-sample.y4m')
options.add_argument('--start-maximized')
options.add_argument('--kiosk')
options.add_argument('--alsa-output-device=plug:amix')
options.add_argument('--enable-logging')
options.add_argument('--vmodule=*=3')

driver = webdriver.Chrome(chrome_options=options, desired_capabilities=d)

#nggyu
URL="https://www.youtube.com/watch?v=dQw4w9WgXcQ"
driver.get(URL)
time.sleep(20)
driver.quit()
exit("Jibri Warmed Up")
#driver.execute_script("window.localStorage.displayname = 'JIBRI'")
#driver.execute_script("window.localStorage.email = 'jibri@mustelinae.net'")

#driver.refresh()
#time.sleep(1)
#driver.find_element_by_id("toolbar_button_camera").click()
#driver.find_element_by_id("toolbar_button_mute").click()

#driver.execute_script("window.localStorage.displayname='Recorder'")
#driver.find_element_by_id("toolbar_button_chat").click()

#driver.quit()


