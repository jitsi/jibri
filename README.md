# Jibri

JItsi BRoadcasting Infrastructure

# What is Jibri

Jibri provides services for recording or streaming a Jitsi Meet conference.

It works by launching a Chrome instance rendered in a virtual framebuffer and capturing and encoding the output with ffmpeg. It is intended to be run on a separate machine (or a VM), with no other applications using the display or audio devices. Only one recording at a time is supported on a single jibri.

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
```bash
sudo add-apt-repository ppa:mc3man/trusty-media
sudo apt-get update
sudo apt-get install ffmpeg
```

### Google Chrome stable & Chromedriver
The latest Google Chrome stable build should be used. It may be able to be installed direclty via apt, but the manual instructions for installing it are as follows:
```bash
curl -sS -o - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list
apt-get -y update
apt-get -y install google-chrome-stable
```
Add chrome managed policies file and set `CommandLineFlagSecurityWarningsEnabled` to `false`. It will hide warnings in Chrome.
You can set it like so:
```bash
mkdir -p /etc/opt/chrome/policies/managed
echo '{ "CommandLineFlagSecurityWarningsEnabled": false }' >>/etc/opt/chrome/policies/managed/managed_policies.json
```
Chromedriver is also required and can be installed like so:
```bash
CHROME_DRIVER_VERSION=`curl -sS chromedriver.storage.googleapis.com/LATEST_RELEASE`
wget -N http://chromedriver.storage.googleapis.com/$CHROME_DRIVER_VERSION/chromedriver_linux64.zip -P ~/
unzip ~/chromedriver_linux64.zip -d ~/
rm ~/chromedriver_linux64.zip
sudo mv -f ~/chromedriver /usr/local/bin/chromedriver
sudo chown root:root /usr/local/bin/chromedriver
sudo chmod 0755 /usr/local/bin/chromedriver
```

### Miscellaneous required tools
See the debian [control file](debian/control) for the dependencies that are required.
These can be installed using the following:
`sudo apt-get install default-jre-headless ffmpeg curl alsa-utils icewm xdotool xserver-xorg-input-void xserver-xorg-video-dummy`

### Jitsi Debian Repository
The Jibri packages can be found in the stable repository on downloads.jitsi.org.
First install the Jitsi repository key onto your system:
```bash
wget -qO - https://download.jitsi.org/jitsi-key.gpg.key | sudo apt-key add -
```
Create a sources.list.d file with the repository:
```bash
sudo sh -c "echo 'deb https://download.jitsi.org stable/' > /etc/apt/sources.list.d/jitsi-stable.list"
```
Update your package list:
```bash
sudo apt-get update
```
Install the latest jibri
```bash
sudo apt-get install jibri
```


### User, group
* Jibri is currently meant to be run as a regular system user. This example creatively uses username 'jibri' and group name 'jibri', but any user will do. This has not been tested with the root user.
* Ensure that the jibri user is in the correct groups to make full access of the audio and video devices. The example jibri account in Ubuntu 16.04 are: "adm","audio","video","plugdev".
```bash
sudo usermod -aG adm,audio,video,plugdev jibri
```

### Config files
* Edit the `jibri.conf` file (installed to `/etc/jitsi/jibri/jibri.conf` by default) appropriately.  You can look at
[reference.conf](src/main/resources/reference.conf) for the default values and an example of how to set up jibri.conf.  Only
override the values you want to change from their defaults in `jibri.conf`.

### Logging
By default, Jibri logs to `/var/log/jitsi/jibri`.  If you don't install via the debian package, you'll need to make sure this directory exists (or change the location to which Jibri logs by editing the [log config](lib/logging.properties)


# Configuring a Jitsi Meet environment for Jibri

Jibri requires some settings to be enabled within a Jitsi Meet configuration. These changes include virtualhosts and accounts in Prosody, settings for the jitsi meet web (within config.js) as well as `jicofo sip-communicator.properties`.

## Prosody

Create the internal MUC component entry.  This is required so that the jibri clients can be discovered by Jicofo in a MUC that's not externally accessible by jitsi meet users.  Add the following in `/etc/prosody/prosody.cfg.lua`:
```lua
-- internal muc component, meant to enable pools of jibri and jigasi clients
Component "internal.auth.yourdomain.com" "muc"
    modules_enabled = {
      "ping";
    }
    storage = "null"
    muc_room_cache_size = 1000
```

Create the recorder virtual host entry, to hold the user account for the jibri chrome session.  This is used to restrict only authenticated jibri chrome sessions to be hidden participants in the conference being recordered.  Add the following in `/etc/prosody/prosody.cfg.lua`:
```lua
VirtualHost "recorder.yourdomain.com"
  modules_enabled = {
    "ping";
  }
  authentication = "internal_plain"
```

Setup the two accounts jibri will use.
```bash
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
Edit the `/etc/jitsi/meet/yourdomain-config.js` file, add/set the following properties:
```javascript
fileRecordingsEnabled: true, // If you want to enable file recording
liveStreamingEnabled: true, // If you want to enable live streaming
hiddenDomain: 'recorder.yourdomain.com',
```
Also make sure that in your interface config (`/usr/share/jitsi-meet/interface_config.js` by default), the `TOOLBAR_BUTTONS` array contains the `recording` value if you want to show the file recording button and the `livestreaming` value if you want to show the live streaming button.

Once recording is enabled in `yourdomain-config.js`, the recording button will become available in the user interface. However, until a valid jibri is seen by Jicofo, the mesage "Recording currently unavailable" will be displayed when it is pressed. Once a jibri connects successfully, the user will instead be prompted to enter a stream key.

**Note**: Make sure to update Jibri's `config.json` appropriately to match any config done above.

## Start Jibri
Once you have configured `config.json`, start the jibri service:
```bash
sudo systemctl restart jibri
```
