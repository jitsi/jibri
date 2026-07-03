# Running jibri locally against a docker-jitsi-meet deployment

This directory contains a script and a docker-compose override that let you
run jibri **from this source tree** while the rest of the Jitsi stack
(prosody, jicofo, jvb, web) runs in docker via
[docker-jitsi-meet](https://github.com/jitsi/docker-jitsi-meet). Useful for
iterating on jibri code changes without rebuilding the docker image.

## Files

- `run-jibri-local.sh` — generates `jibri.conf` + `logging.properties` from
  your `docker-jitsi-meet/.env`, builds jibri if needed, and launches it.
- `prosody.override.yml` — publishes prosody's port 5222 on `127.0.0.1` so
  the host-side jibri can reach it. The default compose only `expose:`s
  this port inside the docker network.

## Prerequisites

- `docker` + `docker compose`
- `mvn` and Java 17 (to build jibri)
- `ffmpeg` on `PATH` (jibri shells out to it for capture)
- A clone of [docker-jitsi-meet](https://github.com/jitsi/docker-jitsi-meet)
  (defaults to `~/dev/docker-jitsi-meet`; override with `DOCKER_JITSI_DIR`)
- A `chromedriver` binary at the jibri repo root, matching your installed
  Chrome milestone (download from <https://googlechromelabs.github.io/chrome-for-testing/>)

## Step by step

### 1. Set up docker-jitsi-meet

```sh
cd ~/dev/docker-jitsi-meet
cp env.example .env
./gen-passwords.sh
mkdir -p ~/.jitsi-meet-cfg/{web,transcripts,prosody/config,prosody/prosody-plugins-custom,jicofo,jvb,jigasi,jibri}
```

### 2. Edit `.env`: enable recording and set `PUBLIC_URL`

Two changes in `~/dev/docker-jitsi-meet/.env`:

**Enable recording.** This is what tells prosody to provision the `jibri`
and `recorder` accounts (using `JIBRI_XMPP_PASSWORD` /
`JIBRI_RECORDER_PASSWORD` from `gen-passwords.sh`) and what makes the web
UI surface the record button. Without it, jibri can't even log into
prosody.

```sh
ENABLE_RECORDING=true
```

**Point `PUBLIC_URL` at your LAN IP.** Jibri opens the conference URL in
Chrome from the host. `localhost` won't do, because Chrome's WebRTC needs
an address that resolves the same way for jibri *and* the other browsers
joining the call. Use the host's LAN address.

Find your local IP:

```sh
# macOS
ipconfig getifaddr en0
# Linux
hostname -I | awk '{print $1}'
```

Then set (HTTP/HTTPS port already exist in env.example — adjust if you
moved them):

```sh
HTTP_PORT=8000
HTTPS_PORT=8443
PUBLIC_URL=https://192.168.1.42:8443   # ← your IP and the HTTPS_PORT
```

The script copies `PUBLIC_URL` into jibri's `base-url`, so jibri opens
`https://192.168.1.42:8443/<room>` instead of the default `https://meet.jitsi/`.

### 3. Bring the stack up with prosody 5222 published

The host-side jibri talks to prosody over XMPP, so we need 5222 reachable
from the host. Apply the override from this directory:

```sh
cd ~/dev/docker-jitsi-meet
docker compose \
  -f docker-compose.yml \
  -f ~/dev/jibri/doc/local-run/prosody.override.yml \
  up -d
```

Confirm prosody is listening on the host:

```sh
nc -zv 127.0.0.1 5222    # → "succeeded!"
```

### 4. Build jibri

```sh
cd ~/dev/jibri
mvn -B -DskipTests clean package
```

The script will also build automatically if no jar is found in `target/`.

### 5. Drop a matching `chromedriver` next to the jibri repo

```sh
# look up your Chrome major version
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --version

# fetch the matching chromedriver and place it at ~/dev/jibri/chromedriver
# (see https://googlechromelabs.github.io/chrome-for-testing/)
chmod +x ~/dev/jibri/chromedriver
```

### 6. (Linux only) Set up audio routing for ffmpeg

Jibri's default ffmpeg config captures audio from the ALSA PCM `plug:bsnoop`,
which is a custom loopback device defined in `/etc/jitsi/jibri/asoundrc` and
backed by the kernel `snd-aloop` module. The .deb postinst sets this up for
the system `jibri` user; if you're running locally as your own user, you
need to do it yourself, or switch to PulseAudio capture.

**Option A — ALSA loopback (matches the deb):**

```sh
# 1. Load the kernel loopback module (creates ALSA card "Loopback")
sudo modprobe snd-aloop
# Persist across reboots
echo snd-aloop | sudo tee /etc/modules-load.d/snd-aloop.conf

# 2. Drop jibri's asoundrc as your user's ALSA config
cp ~/dev/jibri/resources/debian-package/etc/jitsi/jibri/asoundrc ~/.asoundrc

# Verify
aplay -L | grep bsnoop
arecord -D plug:bsnoop -f cd -d 1 /tmp/t.wav && echo OK
```

Caveat: this changes your default ALSA device to a loopback, and on a
modern Ubuntu desktop Chrome routes audio through PulseAudio/PipeWire — so
even with `bsnoop` defined, Chrome won't play into the loopback unless you
also force Chrome to ALSA (e.g. `PULSE_SERVER=/dev/null`).

**Option B — PulseAudio capture (recommended on a desktop dev box):**

Skip the asoundrc/snd-aloop setup entirely and override the audio knobs
when invoking the script:

```sh
JIBRI_AUDIO_SOURCE=pulse \
JIBRI_AUDIO_DEVICE=default \
~/dev/jibri/doc/local-run/run-jibri-local.sh
```

`default` reads from Pulse's default source. To capture system audio
specifically, find the right monitor source with `pactl list short sources`
and pass it as `JIBRI_AUDIO_DEVICE` (e.g. `alsa_output.pci-…analog-stereo.monitor`).

### 6b. (macOS only) Capture the screen instead of the camera

Jibri's default macOS ffmpeg input is `-i 0:0` — whatever avfoundation
device 0 is (usually a camera + the mic). To record the conference you
want a **screen** device for video and a **loopback** device for audio.

1. **Grant Screen Recording permission** to the terminal app you launch
   the script from (System Settings → Privacy & Security → Screen
   Recording), then fully quit and relaunch the terminal. Without it
   avfoundation silently produces black frames.

2. **Install a loopback audio device**, e.g. [BlackHole](https://github.com/ExistentialAudio/BlackHole)
   (`brew install blackhole-2ch`), and route the call audio into it:
   Audio MIDI Setup → `+` → *Create Multi-Output Device* → check both
   BlackHole and your speakers, then select that multi-output device as
   the system sound output. (Selecting BlackHole alone also works, but
   you won't hear anything.)

3. **Find the device indices**:

   ```sh
   ffmpeg -f avfoundation -list_devices true -i ""
   # e.g. video: [2] Capture screen 0   audio: [2] BlackHole 2ch
   ```

4. **Run with the indices**:

   ```sh
   JIBRI_MAC_VIDEO_DEVICE=2 JIBRI_MAC_AUDIO_DEVICE=2 \
   ~/dev/jibri/doc/local-run/run-jibri-local.sh
   ```

Note avfoundation screen devices capture the *whole* display at native
(retina) resolution — there is no per-window capture in ffmpeg on macOS.
The script scales the output down to `JIBRI_RECORDING_RESOLUTION` width,
but whatever else is on that screen ends up in the recording, so keep the
jibri Chrome window in front (or point it at a secondary display, e.g.
`Capture screen 1`).

### 7. Run jibri

```sh
~/dev/jibri/doc/local-run/run-jibri-local.sh
```

You should see:

```
config:       /Users/you/dev/jibri/.local/jibri.conf
jar:          /Users/you/dev/jibri/target/jibri-*-jar-with-dependencies.jar
chromedriver: /Users/you/dev/jibri/chromedriver
xmpp:         127.0.0.1:5222  (domain=meet.jitsi)

… INFO: Jibri starting up …
… INFO: Connecting to xmpp environment on 127.0.0.1 …
… INFO: Joined MUC: jibribrewery@internal-muc.meet.jitsi
```

Now open `https://192.168.1.42:8443/<room>` in two browsers, click the
record button — jibri will pick up the start IQ and join the call.

## Where things land

Generated files live under `~/dev/jibri/.local/`:

| File | Purpose |
|---|---|
| `jibri.conf` | HOCON config built from `.env` |
| `logging.properties` | JUL config (console + ffmpeg/browser file handlers) |
| `recordings/<session-id>/*.mp4` | recorded files |
| `ffmpeg.0.txt` | ffmpeg stderr (JUL captures it; not on console) |
| `browser.0.txt` | chrome browser logs |
| `chromedriver.log` | chromedriver's own log |

## Configuration knobs

All overridable as env vars when invoking the script:

| Var | Default | Notes |
|---|---|---|
| `JIBRI_DIR` | resolved from script path | jibri repo root |
| `DOCKER_JITSI_DIR` | `$HOME/dev/docker-jitsi-meet` | source of `.env` |
| `XMPP_HOST` | `127.0.0.1` | where prosody is reachable |
| `XMPP_PORT` | `5222` | matches `.env` if set there |
| `JIBRI_INSTANCE_ID` | `jibri-local-$$` | shows up as MUC nickname |
| `CHROMEDRIVER` | `$JIBRI_DIR/chromedriver` | path to chromedriver binary |
| `JIBRI_AUDIO_SOURCE` | `alsa` | Linux: ffmpeg `-f` for audio (`alsa`, `pulse`) |
| `JIBRI_AUDIO_DEVICE` | `plug:bsnoop` | Linux: ffmpeg audio input device (e.g. `default` for pulse) |
| `JIBRI_MAC_VIDEO_DEVICE` | unset | macOS: avfoundation video index (use a `Capture screen N` device) |
| `JIBRI_MAC_AUDIO_DEVICE` | unset | macOS: avfoundation audio index (use a loopback device, e.g. BlackHole) |

## Troubleshooting

**`permission denied while trying to connect to the docker API at unix:///var/run/docker.sock`**
— Linux only; your user isn't in the `docker` group. Fix:

```sh
sudo usermod -aG docker $USER
newgrp docker      # apply the new group in this shell, or log out/in
docker ps          # verify
```

Avoid `sudo docker compose …` — files written to `~/.jitsi-meet-cfg` end up
root-owned and bite you later.

**`cannot reach prosody at 127.0.0.1:5222`** — you brought up the stack
without `prosody.override.yml`. Re-run step 3.

**`This version of ChromeDriver only supports Chrome version N`** — your
chromedriver and Chrome milestones don't match. Re-fetch chromedriver for
your Chrome version (step 5).

**Jibri opens `https://meet.jitsi/<room>` instead of your IP** — `PUBLIC_URL`
isn't set in `.env`, or you didn't restart the script after editing `.env`.

**`No such file or directory: /var/log/jitsi/jibri/chromedriver.log`** —
older jibri pinned that path unconditionally. Pull a tree where
`webdriver.chrome.logfile` is overridable, or `mkdir -p` the path with
write permissions.

**ffmpeg quits with `Unknown PCM bsnoop` / `cannot open audio device plug:bsnoop`**
on Linux — you don't have the ALSA loopback set up. Either follow step 6
Option A (snd-aloop + `~/.asoundrc`) or skip ALSA and use Pulse via
`JIBRI_AUDIO_SOURCE=pulse JIBRI_AUDIO_DEVICE=default`.

**ffmpeg quits abruptly with "Error opening output files: Invalid argument"**
on macOS — usually an avfoundation arg conflict. Inspect
`~/dev/jibri/.local/ffmpeg.0.txt` for the real ffmpeg stderr. Note that the
default macOS ffmpeg input is `-i 0:0` (camera + system mic), so the
resulting mp4 records your camera, **not** the chrome window — see step 6b
for screen capture via `JIBRI_MAC_VIDEO_DEVICE`/`JIBRI_MAC_AUDIO_DEVICE`.

**Black video on macOS** — the terminal app that launched the script lacks
the Camera (default `0:0` input) or Screen Recording (screen-capture input)
permission. avfoundation doesn't error on a denied device; it delivers
black frames. Grant the permission in System Settings → Privacy & Security
and fully restart the terminal app.

**Black video on Linux** — you're on a Wayland session
(`echo $XDG_SESSION_TYPE`), where `x11grab` cannot see the screen and
records black. Either log into an Xorg session, or run a dedicated virtual
display and point both Chrome and ffmpeg at it:

```sh
Xvfb :1 -screen 0 1280x720x24 &
DISPLAY=:1 ~/dev/jibri/doc/local-run/run-jibri-local.sh
```

(the second part — overriding ffmpeg's hardcoded `-i :0.0+0,0` grab target —
currently requires editing `input-linux` in the generated conf).
