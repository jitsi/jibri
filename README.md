# Jibri

JItsi BRoadcasting Infrastructure

# What is Jibri

Jibri provides services for recording or streaming a Jitsi Meet conference.

It works by launching a Chrome instance rendered in a virtual framebuffer and capturing and encoding the output with ffmpeg. It is intended to be run on a separate machine (or a VM), with no other applications using the display or audio devices. Only one recording at a time is supported on a single jibri.



# Installing Jibri

### Installation notes
* Jibri was built on ubuntu 16 (Xenial), and has been tested with the pre-built kernel and extra kernel modules ( linux-image-extra-virtual package). Any other distribution or kernel configuration MAY work but has not been tested.

## Pre-requisites
### ALSA and Loopback Device
* First make sure the ALSA loopback module is available. The extra modules (including ALSA loopback) can be installed on Ubuntu 16.04 using package name linux-image-extra-virtual
* Perform the following tasks as the root user
  * Set up the module to be loaded on boot: echo "snd-aloop" >> /etc/modules
  * Load the module into the running kernel: modprobe snd-aloop
  * Check to see that the module is already loaded: lsmod | grep snd_aloop
* If the output shows the snd-aloop module loaded, then the ALSA loopback configuration step is complete.

### Ffmpeg with X11 capture support
* Jibri requires a relatively modern ffmpeg install with x11 capture compiled in. This comes by default in Ubuntu 16.04, by installing the ffmpeg package.
* If building Jibri for Ubuntu 14 (trust), the mc3man repo provides packages. They can be used by the following in Ubuntu 16:
  * add-apt-repository ppa:mc3man/trusty-media
  * apt-get update
  * apt-get install ffmpeg

### Google chrome stable & Chromedriver
The latest google chrome stable build should be used. It may be able to be installed direclty via apt, but the manual instructions for installing it are as follows:
```
curl -sS -o - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list
apt-get -y update
apt-get -y install google-chrome-stable
```
Chromedriver is also required and can be installed like so:
```
CHROME_DRIVER_VERSION=`curl -sS chromedriver.storage.googleapis.com/LATEST_RELEASE`
wget -N http://chromedriver.storage.googleapis.com/$CHROME_DRIVER_VERSION/chromedriver_linux64.zip -P ~/
unzip ~/chromedriver_linux64.zip -d ~/
rm ~/chromedriver_linux64.zip
sudo mv -f ~/chromedriver /usr/local/bin/chromedriver
sudo chown root:root /usr/local/bin/chromedriver
sudo chmod 0755 /usr/local/bin/chromedriver
```

### Miscelaneous required tools
See the debian [control file](resources/debian-package-jibri/DEBIAN/control) for the dependencies that are required.

### User, group
* Jibri is currently meant to be run as a regular system user. This example creatively uses username 'jibri' and group name 'jibri', but any user will do. This has not been tested with the root user.
* Ensure that the jibri user is in the correct groups to make full access of the audio and video devices. The example jibri account in Ubuntu 16 are: "adm","audio","video","plugdev".

### Config files
* Copy the asoundrc file to /home/jibri/.asoundrc to ensure that the ALSA loopback driver is configured properly for the jibri user.
* Edit the config.json file (installed to /etc/jitsi/jibri/config.json by default) appropriately.

### Logging
By default, Jibri logs to `/var/log/jitsi/jibri`.  If you don't install via the debian package, you'll need to make sure this directory exists (or change the location to which Jibri logs by editing the [log config](lib/logging.properties)

### Support daemon systemd configurations
* Jibri expects XOrg and icewm to be running as daemons. To accomplish this, copy the example systemd files from the systemd directory into the appropriate place (/etc/systemd/system/ on Ubuntu 16).
  * TODO: (where will the icewm/xorg systemd scripts be put?)
* To start the supporting services:
  * `service jibri-xorg start`
  * `service jibri-icewm start`
* To start the jibri controller daemon (will not start properly until config.json is properly configured for your Jitsi Meet install):
  * `service jibri start`

# Configuring a Jitsi Meet environment for Jibri

Jibri requires some settings to be enabled within a Jitsi Meet configuration. This changes include virtualhosts and accounts in Prosody, settings for the jitsi meet web (within config.js) as well as jicofo sip-communicator.properties.

## Prosody
Create the recorder virtual host entry in /etc/prosody/prosody.cfg.lua:
```
VirtualHost "recorder.yourdomain.com"
  modules_enabled = {
    "ping";
  }
  authentication = "internal_plain"
```
Setup the two accounts jibri will use.
```
prosodyctl register jibri auth.yourdomain.com jibriauthpass
prosodyctl register recorder recorder.yourdomain.com jibrirecorderpass
```
The first account is the one Jibri will use to log into the control MUC (where Jibri will send its status and await commands).  The second account is the one Jibri will use as a client in selenium when it joins the call so that it can be treated in a special way by the Jitsi Meet web UI.

## Jicofo
Edit /etc/jitsi/jicofo/sip-communicator.properties (or similar), set the appropriate MUC to look for the Jibri Controllers. This should be the same MUC as is referenced in jibri's config.json file. Restart Jicofo after setting this property. It's also suggested to set the PENDING_TIMEOUT to 90 seconds, to allow the Jibri some time to start up before being marked as failed.
```
org.jitsi.jicofo.jibri.BREWERY=TheBrewery@conference.yourdomain.com
org.jitsi.jicofo.jibri.PENDING_TIMEOUT=90
```

## Jitsi Meet
Edit the /etc/jitsi/meet/yourdomain.config.js file, add/set the following properties:
```
recordingType: 'jibri',
enableRecording: true,
hiddenDomain: ‘recorder.yourdomain.com',
```
Once recording is enabled in config.js, the recording button will become available in the user interface. However, until a valid jibri is seen by Jicofo, the mesage "Recording currently unavailable" will be displayed when it is pressed. Once a jibri connects successfully, the user will instead be prompted to enter a stream key.

Make sure to update Jibri's config.json appropriately to match any config done above.
