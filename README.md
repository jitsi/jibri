# What is Jibri

Jibri is a set of tools for recording and/or streaming a Jitsi Meet conference.
It is currently very experimental.

It works by launching a Chrome instance rendered in a virtual framebuffer and
capturing and encoding the output with ffmpeg. It is intended to be run on a
separate machine (or a VM), with no other applications using the display or
audio devices.  Only one recording at a time is supported on a single jibri.

Jibri consists of several pieces:

### 1. A set of supporting services required for jibri operation

These are external dependencies which need to be installed and running on the VM.
Example systemd startup files are provided in ```systemd``` and should be modified and copied into the appropriate place depending on your system type.


### 2. A set of scripts for starting/stopping the recording components

These reside in ```scripts```. They start and stop the ffmpeg, browser and other external processes.

### 2. An XMPP client

This is a simple XMPP client which resides in ```jibri-xmpp-client```. It
provides an external interface for the recording functionality. It connects to
a set of XMPP servers, enters a pre-defined MUC on each, advertises its status
there, and accepts commands (start/stop recording) coming from a controlling
agent (i.e. jicofo).  This daemon is the main controller for the Jibri Recorder.


# Installing Jibri

### Installation Notes

* Jibri was built on ubuntu 16 (Xenial), and has been tested with the pre-built kernel and extra kernel modules ( linux-image-extra-virtual package).  Any other distribution or kernel configuration MAY work but have not been tested.


### ALSA and Loopback Device

* First make sure the ALSA loopback module is available.  The extra modules (including ALSA loopback) can be installed on Ubuntu 16.04 using package name ```linux-image-extra-virtual```

* Perform the following tasks as the root user

  - Set up the module to be loaded on boot: `echo "snd-aloop" >> /etc/modules`

  - Load the module into the running kernel: `modprobe snd-aloop`

  - Check to see that the module is already loaded:  `lsmod | grep snd_aloop`

* If the output shows the snd-aloop module loaded, then the ALSA loopback configuration step is complete.


### ffmpeg with X11 capture support

* Jibri requires a relatively modern ffmpeg install with x11 capture compiled in.  This comes by default in Ubuntu 16.04, by installing the ```ffmpeg``` package.

* If building Jibri for Ubuntu 14 (trust), he mc3man repo provides packages.  They can be used by the following in Ubuntu 16:

  - `add-apt-repository ppa:mc3man/trusty-media`
  - `apt-get update`
  - `apt-get install ffmpeg`


### Google Chrome Stable

* The latest google chrome stable build should be used.  These steps work for installing on Ubuntu 16:
  - `wget https://dl.google.com/linux/linux_signing_key.pub`
  - `apt-key add linux_signing_key.pub`
  - `echo "deb http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/dl_google_com_linux_chrome_deb.list`
  - `apt-get update`
  - `apt-get install google-chrome-stable`


### Miscellaneous required tools

* Jibri scripts make use of certain other external libraries and tools.

* The following packages in Ubuntu 16:
  - alsa-utils
  - ffmpeg
  - icewm  
  - jq
  - python-pip
  - python3
  - python3-pip
  - xdotool
  - xserver-xorg-input-void
  - xserver-xorg-video-dummy


* A newer Chromedriver is also required.  It can be downloaded from google (https://sites.google.com/a/chromium.org/chromedriver/downloads).  Grab the one labeled chromedriver_linux64.zip - the following will download, and copy to the correct folder:
  
  - `CHD_VER=curl -L http://chromedriver.storage.googleapis.com/LATEST_RELEASE` 
  - `wget https://chromedriver.storage.googleapis.com/$CHD_VER/chromedriver_linux64.zip`
  - `unzip chromedriver_linux64.zip`
  - `cp chromedriver /usr/bin/chromedriver`


### User, Group

* Jibri is currently meant to be run as a regular system user.  This example creatively uses username 'jibri' and group name 'jibri', but any user will do.  This has not been tested with the root user.

* Ensure that the jibri user is in the correct groups to make full access of the audio and video devices.  The example jibri account in Ubuntu 16 are: "adm","audio","video","plugdev"

* The following will add the 'jibri' user with the appropriate groups. Run as root:
  - `useradd -G adm,audio,vido,plugdev jibri`

### Jibri directory

* Set up the jibri home directory (for example /home/jibri)

* Check out the jibri source code: `git clone https://github.com/jitsi/jibri.git`

* Copy the two main directories into place.  In this example install, the ```jibri-xmpp-client``` and ```scripts``` directories should be copied to /home/jibri/ so that they are siblings.  Many scripts referenced in the jibri daemon currently assume the scripts directory to be available from ../scripts/

  `cp -a jibri/jibri-xmpp-client /home/jibri`
  `cp -a jibri/scripts /home/jibri`


* Copy the asoundrc file to /home/jibri/.asoundrc to ensure that the ALSA loopback driver is configured properly for the jibri user.
   `cp jibri/asoundrc /home/jibri/.asoundrc`

* Copy the icewm.preferences connecting.png files to /home/jibri/.icewm/ for the startup/stop desktop experience with jibri.
   `mkdir /home/jibri/.icewm`
   `cp jibri/icewm.preferences /home/jibri/.icewm/preferences`
   `cp jibri/connecting.png /home/jibri/.icewm/connecting.png`

* Copy the config.json into place, then modify it to connect to your existing Jitsi Meet install.  Refer to the section ```Connecting Jibri to Jitsi Meet``` for details on contents of config.json
   `cp jibri/config.json /home/jibri/config.json`

### Python dependencies

* Use pip3 to install all the python related dependencies
  - `pip3 install setuptools`
  - `pip3 install -r jibri/requirements.txt`


### Support daemon systemd configurations

* Jibri expects XOrg and icewm to be running as daemons.  To accomplish this, copy the example systemd files from the ```systemd``` directory into the appropriate place (/etc/systemd/system/ on Ubuntu 16).

  - `cp jibri/systemd/*.service /etc/systemd/system/`

* To start the supporting services

  - `service jibri-xorg start`
  - `service jibri-icewm start`

* To start the jibri controller daemon (will not start properly until config.json is properly configured for your Jitsi Meet install)

  - `service jibri-xmpp start`

## Configuring Jibri

### Connecting Jibri to Jitsi Meet

* Jibri requires some settings to be enabled within a Jitsi Meet configuration.  This changes include virtualhosts and accounts in Prosody, settings for the jitsi meet web (within config.js) as well as jicofo sip-communicator.properties

* Network: Make sure that port 5222 on your Jicofo instance is open to the Jibri instance.  This may means some combination of Security Groups (if in AWS), or local firewall configuration.

* Prosody

  - Create the recorder virtual host entry in /etc/prosody/prosody.cfg.lua :

  ```
  VirtualHost "recorder.yourdomain.com"
    modules_enabled = {
      "ping";
    }
    authentication = "internal_plain"
   ```

  - Restart prosody daemon after edits are complete


* Prosody: create accounts for the two methods Jibri uses to connect
```
    prosodyctl register jibri auth.yourdomain.com jibripass
    prosodyctl register recorder recorder.yourdomain.com jibripass
```
The first account is the jibri username and password.  The second account is the selenium username and password.  Record the passwords you choose, as they will be used in config.json below.


* Jicofo: Edit /etc/jitsi/jicofo/sip-communicator.properties (or similar), set the appropriate MUC to look for the Jibri Controllers.  This should be the same MUC as is referenced in jibri's config.json file.  Restart Jicofo after setting this property.  It's also suggested to set the PENDING_TIMEOUT to 90 seconds, to allow the Jibri some time to start up before being marked as failed.
```
  org.jitsi.jicofo.jibri.BREWERY=TheBrewery@conference.yourdomain.com
  org.jitsi.jicofo.jibri.PENDING_TIMEOUT=90
 ```


* Jitsi Meet: Edit the /etc/jitsi/meet/yourdomain.config.js file, add/set the following properties:
```
   recordingType: 'jibri',

   enableRecording: true,

   hiddenDomain: ‘recorder.yourdomain.com',
```
Once recording is enabled in config.js, the recording button will become available in the user interface.  However, until a valid jibri is seen by Jicofo, the mesage "Recording currently unavailable" will be displayed when it is pressed.  Once a jibri connects successfully, the user will instead be prompted to enter a stream key.

* Final Jibri configuration: edit /home/jibri/config.json and enter the IP Address or DNS name of your host in the "servers" section of the JSON.  Also update the password, xmpp_domain, selenium_xmpp_password as appropriate for your install.

* Start the jibri daemon
  - `service jibri-xmpp start`

* Open a new Jitsi Meet URL and see if the recording option is enabled and prompts for a stream key.  Congratulations, you've got a jibri!

### Manually testing Jibri

* To test the selenium side of Jibri (can it connect to a Jitsi Meet conference) you can use the following:
  - Become the ```jibri``` user: `sudo su - jibri`
  - `cd /home/jibri/jibri-xmpp-client`
  - `DISPLAY=:0 ./jibriselenium.py -u https://URL-TO-MEETING`

  This will start up a selenium session with chrome, and connect to your jitsi meeting, detect if it's receiving any audio/video, wait 60 seconds and then exit.

* To test the ffmpeg capture component of Jibri (can it read from X11, stream to a file, for example filename.flv)

  - Become the ```jibri``` user: `sudo su - jibri`
  - `cd /home/jibri/scripts`
  - `./start-ffmpeg.sh filename.flv`
  - if it starts OK, wait some time then kill it: `./stop_recording.sh`
  - examine the output file `/tmp/jibri-ffmpeg.out` for success
  - Download and play `filename.flv` to test recording success

* To test the youtube streaming side of Jibri (after ffmpeg is confirmed to work), first find a valid youtube stream key (https://jitsi.org/live for details), replace STREAM_KEY below

   - Become the ```jibri``` user: `sudo su - jibri`
   - `cd /home/jibri/scritps`
   - `./launch_recording.sh null null null STREAM_KEY`
   - check your youtube livestreaming dashboard for the status (https://youtube.com/live_dashboard)
   - `./stop_recording.sh` to kill the stream


# Troubleshooting

* To see if recording audio works:
    1. Play something
    2. Record. The default ALSA playback device is hw:0,0. Its corresponding capture device is hw:0,1. If something is already playing (hw:0:0 is open) you may need to match its settings with the -f, -c and -r arguments to arecord.
        2A. `arecord -D hw:0,1,0  -f S16_LE -c 2 -r 44100 test.wav`
        2B. `ffmpeg -f alsa -i hw:0,1,0 test.wav`
    2. Play something
    3. See if something was recorded with `xxd test.wav | grep -v ' 0000 ' | wc -l`. You should see more than one line.

* To record to a file instead of youtube, give start-ffmpeg.sh a local file name (flv) in $1.

