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

### 6. Run jibri

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

## Troubleshooting

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

**ffmpeg quits abruptly with "Error opening output files: Invalid argument"**
on macOS — usually an avfoundation arg conflict. Inspect
`~/dev/jibri/.local/ffmpeg.0.txt` for the real ffmpeg stderr. Note that the
default macOS ffmpeg input is `-i 0:0` (FaceTime camera + system mic), so
the resulting mp4 will record your camera, **not** the chrome window. Native
screen capture on macOS requires switching to a screen-capture device index
plus a virtual audio loopback (e.g. BlackHole) — out of scope for this
script.
