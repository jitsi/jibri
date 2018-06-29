# Jibri

JItsi BRoadcasting Infrastructure

# What is Jibri

Jibri provides services for recording or streaming a Jitsi Meet conference.

It works by launching a Chrome instance rendered in a virtual framebuffer and capturing and encoding the output with ffmpeg. It is intended to be run on a separate machine (or a VM), with no other applications using the display or audio devices. Only one recording at a time is supported on a single jibri.

### What happened to the old Jibri?

Jibri was originally written in a combination of python and bash, but we were facing some stability issues so it was re-written.  The original code can still be found in a branch [here](https://github.com/jitsi/jibri/tree/python_jibri).
##### Differences between the old and new Jibri
From an API perspective, the new Jibri is the same as the old one with regards to backwards compatibility.  The XMPP API, which is the primary way we interact with Jibri, is exactly the same.  The new Jibri adds an HTTP API as well, though it does not have 100% parity with the XMPP API and, as of this point, was used mainly during testing to trigger things more easily.  The new Jibri code features another HTTP API for Jibri management (e.g. shutdown, graceful shutdown, health checks).

The new Jibri has a reorganized config file format.  A sample of the config can be found [here](resources/debian-package/etc/jitsi/jibri/config.json).  Your old Jibri config will **not** work with the new Jibri without adapting it to the new format.

The new Jibri now has configurable logging, which can be set via the [logging.properties](lib/logging.properties) file.

# Installing Jibri

### Installation notes
* Jibri was built on ubuntu 16.04 (Xenial), and has been tested with the pre-built kernel and extra kernel modules (`linux-image-extra-virtual` package). Any other distribution or kernel configuration MAY work but has not been tested.

## Pre-requisites
### ALSA and Loopback Device
* First make sure the ALSA loopback module is available. The extra modules (including ALSA loopback) can be installed on Ubuntu 16.04 using package name `linux-image-extra-virtual`
* Perform the following tasks as the root user
  * Set up the module to be loaded on boot: `echo "snd-aloop" >> /etc/modules`
  * Load the module into the running kernel: `modprobe snd-aloop`
  * Check to see that the module is already loaded: `lsmod | grep snd_aloop`
* If the output shows the snd-aloop module loaded, then the ALSA loopback configuration step is complete.

### Ffmpeg with X11 capture support
* Jibri requires a relatively modern ffmpeg install with x11 capture compiled in. This comes by default in Ubuntu 16.04, by installing the ffmpeg package.
* If building Jibri for Ubuntu 14.04 (trusty), the mc3man repo provides packages. They can be used by the following in Ubuntu 14.04:
  * `sudo add-apt-repository ppa:mc3man/trusty-media`
  * `sudo apt-get update`
  * `sudo apt-get install ffmpeg`

### Google Chrome stable & Chromedriver
The latest Google Chrome stable build should be used. It may be able to be installed direclty via apt, but the manual instructions for installing it are as follows:
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

### Miscellaneous required tools
See the debian [control file](debian/jibri/DEBIAN/control) for the dependencies that are required.
These can be installed using the following:
`sudo apt-get install default-jre-headless ffmpeg curl alsa-utils icewm xdotool xserver-xorg-input-void xserver-xorg-video-dummy`

### Jitsi Debian Repository
The latest Jibri packages can be found in the unstable repository on downloads.jitsi.org.
First install the Jitsi repository key onto your system:
```
wget -qO - https://download.jitsi.org/jitsi-key.gpg.key | sudo apt-key add -
```
Create a sources.list.d file with the repository:
```
sudo sh -c "echo 'deb https://download.jitsi.org unstable/' > /etc/apt/sources.list.d/jitsi-unstable.list"
```
Update your package list:
```
sudo apt-get update
```
Install the latest jibri
```
sudo apt-get install jibri
```


### User, group
* Jibri is currently meant to be run as a regular system user. This example creatively uses username 'jibri' and group name 'jibri', but any user will do. This has not been tested with the root user.
* Ensure that the jibri user is in the correct groups to make full access of the audio and video devices. The example jibri account in Ubuntu 16.04 are: "adm","audio","video","plugdev".
`sudo usermod -aG adm,audio,video,plugdev jibri`

### Config files
* Edit the `config.json` file (installed to `/etc/jitsi/jibri/config.json` by default) appropriately.

### Logging
By default, Jibri logs to `/var/log/jitsi/jibri`.  If you don't install via the debian package, you'll need to make sure this directory exists (or change the location to which Jibri logs by editing the [log config](lib/logging.properties)


# Configuring a Jitsi Meet environment for Jibri

Jibri requires some settings to be enabled within a Jitsi Meet configuration. These changes include virtualhosts and accounts in Prosody, settings for the jitsi meet web (within config.js) as well as `jicofo sip-communicator.properties`.

## Prosody

Create the internal MUC component entry.  This is required so that the jibri clients can be discovered by Jicofo in a MUC that's not externally accessible by jitsi meet users.  Add the following in `/etc/prosody/prosody.cfg.lua`:
```
-- internal muc component, meant to enable pools of jibri and jigasi clients
Component "internal.auth.yourdomain.com" "muc"
    modules_enabled = {
      "ping";
    }
    storage = "null"
    muc_room_cache_size = 1000
```

Create the recorder virtual host entry, to hold the user account for the jibri chrome session.  This is used to restrict only authenticated jibri chrome sessions to be hidden participants in the conference being recordered.  Add the following in `/etc/prosody/prosody.cfg.lua`:
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
Edit `/etc/jitsi/jicofo/sip-communicator.properties` (or similar), set the appropriate MUC to look for the Jibri Controllers. This should be the same MUC as is referenced in jibri's `config.json` file. Restart Jicofo after setting this property. It's also suggested to set the PENDING_TIMEOUT to 90 seconds, to allow the Jibri some time to start up before being marked as failed.
```
org.jitsi.jicofo.jibri.BREWERY=JibriBrewery@internal.auth.yourdomain.com
org.jitsi.jicofo.jibri.PENDING_TIMEOUT=90
```

## Jitsi Meet
Edit the `/etc/jitsi/meet/yourdomain.config.js` file, add/set the following properties:
```
fileRecordingsEnabled: true, // If you want to enable file recording
liveStreamingEnabled: true, // If you want to enable live streaming
hiddenDomain: 'recorder.yourdomain.com',
```
Also make sure that in your interface config (`/usr/share/jitsi-meet/interface_config.js` by default), the `TOOLBAR_BUTTONS` array contains the `recording` value if you want to show the file recording button and the `livestreaming` value if you want to show the live streaming button.

Once recording is enabled in config.js, the recording button will become available in the user interface. However, until a valid jibri is seen by Jicofo, the mesage "Recording currently unavailable" will be displayed when it is pressed. Once a jibri connects successfully, the user will instead be prompted to enter a stream key.

Make sure to update Jibri's config.json appropriately to match any config done above.

## Start Jibri
Once you have configured `config.json`, start the jibri service:
`sudo systemctl restart jibri`
