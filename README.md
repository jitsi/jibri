# Jibri

JItsi BRoadcasting Infrastructure

# What is Jibri

Jibri provides services for recording or streaming a Jitsi Meet conference.

It works by launching a Chrome instance rendered in a virtual framebuffer and
capturing and encoding the output with ffmpeg. It is intended to be run on a
separate machine (or a VM), with no other applications using the display or
audio devices. Only one recording at a time is supported on a single jibri.

**NOTE:** Jibri currently only works with a full-fledged Jitsi Meet
installation. Using a different frontend won't work.

# Installing Jibri

### Installation notes

- Jibri was built on Ubuntu 18.04 (Bionic), and has been tested with the
  pre-built kernel and extra kernel modules (`linux-image-extra-virtual`
  package). Any other distribution or kernel configuration MAY work but has not
  been tested.

## Pre-requisites

### ALSA and Loopback Device

- First make sure the ALSA loopback module is available. The extra modules
  (including ALSA loopback) can be installed on Ubuntu 16.04 using package name
  `linux-image-extra-virtual`
- Perform the following tasks as the root user
  - Set up the module to be loaded on boot: `echo "snd_aloop" >> /etc/modules`
  - Load the module into the running kernel: `modprobe snd_aloop`
  - Check to see that the module is already loaded: `lsmod | grep snd_aloop`
- If the output shows the snd-aloop module loaded, then the ALSA loopback
  configuration step is complete.

### Ffmpeg with X11 capture support

- Jibri requires a relatively modern ffmpeg install with x11 capture compiled
  in. This comes by default in Ubuntu 16.04, by installing the ffmpeg package.
- If building Jibri for Ubuntu 14.04 (trusty), the mc3man repo provides
  packages. They can be used by the following in Ubuntu 14.04:

```bash
sudo add-apt-repository ppa:mc3man/trusty-media
sudo apt-get update
sudo apt-get install ffmpeg
```

### Google Chrome stable & Chromedriver

The latest Google Chrome stable build should be used. It may be able to be
installed direclty via apt, but the manual instructions for installing it are as
follows:

```bash
sudo su -l
apt-get -y install wget curl gnupg jq unzip

curl -fsSL https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome-keyring.gpg] http://dl.google.com/linux/chrome/deb/ stable main" | tee /etc/apt/sources.list.d/google-chrome.list

apt-get -y update
apt-get -y install google-chrome-stable
apt-mark hold google-chrome-stable
```

Add chrome managed policies file and set
`CommandLineFlagSecurityWarningsEnabled` to `false`. It will hide warnings in
Chrome. You can set it like so:

```bash
mkdir -p /etc/opt/chrome/policies/managed
echo '{ "CommandLineFlagSecurityWarningsEnabled": false }' >>/etc/opt/chrome/policies/managed/managed_policies.json
```

Chromedriver is also required and can be installed like so:

```bash
CHROME_VER=$(dpkg -s google-chrome-stable | egrep "^Version" | cut -d " " -f2 | cut -d. -f1-3)
CHROMELAB_LINK="https://googlechromelabs.github.io/chrome-for-testing"
CHROMEDRIVER_LINK=$(curl -s $CHROMELAB_LINK/known-good-versions-with-downloads.json | jq -r ".versions[].downloads.chromedriver | select(. != null) | .[].url" | grep linux64 | grep "$CHROME_VER" | tail -1)
wget -O /tmp/chromedriver-linux64.zip $CHROMEDRIVER_LINK

rm -rf /tmp/chromedriver-linux64
unzip -o /tmp/chromedriver-linux64.zip -d /tmp
mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/
chown root:root /usr/local/bin/chromedriver
chmod 755 /usr/local/bin/chromedriver
```

### Jitsi Debian Repository

The Jibri packages can be found in the stable repository on downloads.jitsi.org.
First install the Jitsi repository key onto your system:

```bash
curl https://download.jitsi.org/jitsi-key.gpg.key | sudo sh -c 'gpg --dearmor > /usr/share/keyrings/jitsi-keyring.gpg'
```

Create a sources.list.d file with the repository:

```bash
echo 'deb [signed-by=/usr/share/keyrings/jitsi-keyring.gpg] https://download.jitsi.org stable/' | sudo tee /etc/apt/sources.list.d/jitsi-stable.list > /dev/null
```

Update your package list:

```bash
sudo apt-get update
```

Install the latest jibri

```bash
sudo apt-get install jibri
```

### User and Group

- Jibri is currently meant to be run as a regular system user. This example
  creatively uses username 'jibri' and group name 'jibri', but any user will do.
  This has not been tested with the root user.
- Ensure that the jibri user is in the correct groups to make full access of the
  audio and video devices. The example jibri account in Ubuntu 16.04 are:
  "adm","audio","video","plugdev".

```bash
sudo usermod -aG adm,audio,video,plugdev jibri
```

### Config files

- Edit the `jibri.conf` file (installed to `/etc/jitsi/jibri/jibri.conf` by
  default) appropriately. You can look at
  [reference.conf](src/main/resources/reference.conf) for the default values and
  an example of how to set up jibri.conf. Only override the values you want to
  change from their defaults in `jibri.conf`.

```
jibri {
    .....
    api {
        xmpp {
            environments = [
                {
                    name = "yourdomain.com"
                    xmpp-server-hosts = ["1.2.3.4"],
                    xmpp-domain = "yourdomain.com"
                    control-login {
                        domain = "auth.yourdomain.com"
                        username = "jibri"
                        password = "jibriauthpass"
                        port = 5222
                    }
                    control-muc {
                        domain = "internal.auth.yourdomain.com"
                        room-name = "JibriBrewery"
                        nickname = "myjibri-1-2-3-4"
                    }
                    call-login {
                        domain = "recorder.yourdomain.com"
                        username = "recorder"
                        password = "jibrirecorderpass"
                    }
                    strip-from-room-domain = "conference."
                    trust-all-xmpp-certs = true
                    usage-timeout = 0
                }
            ]
        }
    }
    .....
}
```

### Logging

By default, Jibri logs to `/var/log/jitsi/jibri`. If you don't install via the
debian package, you'll need to make sure this directory exists (or change the
location to which Jibri logs by editing the [log config](lib/logging.properties)

# Configuring a Jitsi Meet environment for Jibri

Jibri requires some settings to be enabled within a Jitsi Meet configuration.
These changes include virtualhosts and accounts in Prosody, settings for the
jitsi meet web (within config.js) as well as `jicofo.conf`.

## Prosody

Create the internal MUC component entry. This is required so that the jibri
clients can be discovered by Jicofo in a MUC that's not externally accessible by
jitsi meet users. Add the following in `/etc/prosody/prosody.cfg.lua`:

```lua
-- internal muc component, meant to enable pools of jibri and jigasi clients
Component "internal.auth.yourdomain.com" "muc"
    modules_enabled = {
      "ping";
    }
    -- storage should be "none" for prosody 0.10 and "memory" for prosody 0.11
    storage = "memory"
    muc_room_cache_size = 1000
```

Create the recorder virtual host entry, to hold the user account for the jibri
chrome session. This is used to restrict only authenticated jibri chrome
sessions to be hidden participants in the conference being recordered. Add the
following in `/etc/prosody/prosody.cfg.lua`:

```lua
VirtualHost "recorder.yourdomain.com"
  modules_enabled = {
    "ping";
  }
  authentication = "internal_hashed"
```

Setup the two accounts jibri will use:

```bash
prosodyctl register jibri auth.yourdomain.com jibriauthpass
prosodyctl register recorder recorder.yourdomain.com jibrirecorderpass
```

The first account is the one Jibri will use to log into the control MUC (where
Jibri will send its status and await commands). The second account is the one
Jibri will use as a client in selenium when it joins the call so that it can be
treated in a special way by the Jitsi Meet web UI.

## Jicofo

Edit `/etc/jitsi/jicofo/jicofo.conf`, set the appropriate MUC to look for the
Jibri Controllers. This should be the same MUC as is referenced in jibri's
`jibri.conf` file. Restart Jicofo after setting this property. It's also
suggested to set the pending-timeout to 90 seconds, to allow the Jibri some time
to start up before being marked as failed.

```
jicofo {
  ...
  jibri {
    brewery-jid = "JibriBrewery@internal.auth.yourdomain.com"
    pending-timeout = 90 seconds
  }
  ...
}
```

## Jitsi Meet

Edit the `/etc/jitsi/meet/yourdomain-config.js` file, add/set the following
properties:

```javascript
// recording
config.recordingService = {
  enabled: true,
  sharingEnabled: true,
  hideStorageWarning: false,
};

// liveStreaming
config.liveStreaming = {
  enabled: true,
};

config.hiddenDomain = "recorder.yourdomain.com";
```

Once recording is enabled in `yourdomain-config.js`, the recording button will
become available in the user interface. However, until a valid jibri is seen by
Jicofo, the mesage "Recording currently unavailable" will be displayed when it
is pressed. Once a jibri connects successfully, the user will instead be
prompted to enter a stream key.

**Note**: Make sure to update Jibri's `jibri.conf` appropriately to match any
config done above.

## Start Jibri

Once you have configured `jibri.conf`, start the jibri service:

```bash
sudo systemctl restart jibri
```
