# What is Jibri

Jibri is a set of tools for recording and/or streaming a Jitsi Meet conference.
It is currently very experimental.

It works by launching a Chrome instance rendered in a virtual framebuffer and
capturing and encoding the output with ffmpeg. It is intended to be run on a
separate machine (or a VM), with no other applications using the display or
audio devices.

Jibri consists of two parts:

### 1. A set of scripts to start/stop all needed services

These reside in ```scripts```. They start and stop the X server, window manager,
ffmpeg and the browser processes.

### 2. An XMPP client

This is a simple XMPP client which resides in ```jibri-xmpp-client```. It
provides an external interface for the recording functionality. It connects to
a set of XMPP servers, enters a pre-defined MUC on each, advertises its status
there, and accepts commands (start/stop recording) coming from a controlling
agent (i.e. jicofo).



# Using Jibri

These are some very rough notes on how to setup a machine for jibri:

* Make sure the user you use is in all needed groups. Not sure which ones are
  required, but they're a subset of {ubuntu, adm, dialout, cdrom, floppy, sudo,
  audio, dip, video, plugdev, netdev}.
* Load the needed alsa-modules:
    modprobe snd-aloop
* Copy asoundrc to ~/.asoundrc
* Ensure that the latest ffmpeg is installed:

    sudo add-apt-repository ppa:mc3man/trusty-media
    sudo apt-get update
    sudo apt-get dist-upgrade

* Install all required software:
  - alsa + snd_aloop
  - pulse
  - Xorg, video-dummy
  - icewm
  - chrome/chromium
  - chromedriver
  - python (pip): selenium
  - ffmpeg (preferably the real thing, not libav)
  - mplayer, imagemagick



## Running

### Manually

```sh
./scripts/launch_recording.sh <URL> <OUTPUT_FILENAME> [TOKEN] [YOUTUBE_STREAM_ID]
```

- URL is the address of the Jitsi Meet conference.
- OUTPUT_FILENAME is the name of the output file.
- TOKEN is ignored.
- YOUTUBE_STREAM_ID is the ID of the youtube stream to use (accessible from the youtube interface). If provided, this will replace OUTPUT_FILENAME.

### With the XMPP client:

You need to:

- Configure the Jitsi Meet client to use jibri (?)
- Run jibri-xmpp-client (see `jibri-xmpp-client/README`

### With Docker

- Build the Dockerfile: `docker build -t jibri:git .`
- Run the Dockerfile: `docker run -e "JIBRI_JID=…" -e "JIBRI_NICK=…" -t jibri:git`

You will also need to set the following environment variables when running the
docker file:

- JIBRI_JID
- JIBRI_NICK
- JIBRI_PASS
- JIBRI_ROOM
- JIBRI_ROOMPASS
- JIBRI_TOKEN_SERVERS

or create a new Dockerfile based on `jitsi/jibri:latest` with your own
`/root/config.json` file and the CMD:

```docker
CMD ["dumb-init", "python3", "./jibri-xmpp-client/app.py", "-c", "config.json"]
```

# Troubleshooting

* If a pulse process uses too much CPU, try disabling some stuff in /etc/pulse/default.pa:

```
load-module module-filter-heuristics
load-module module-filter-apply
```

* To remove the mouse pointer (more like kill the mouse itself with an RPG):
  `rmmod psmouse`

* To see if recording audio works:
    1. Play something
    2. Record. The default ALSA playback device is hw:0,0. Its corresponding capture device is hw:0,1. If something is already playing (hw:0:0 is open) you may need to match its settings with the -f, -c and -r arguments to arecord.
        2A. `arecord -D hw:0,1,0  -f S16_LE -c 2 -r 44100 test.wav`
        2B. `ffmpeg -f alsa -i hw:0,1,0 test.wav`
    2. Play something
    3. See if something was recorded with `xxd test.wav | grep -v ' 0000 ' | wc -l`. You should see more than one line.

* To record to a file instead of youtube, give start-ffmpeg.sh a local file name (flv) in $1.

