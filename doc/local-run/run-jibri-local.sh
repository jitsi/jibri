#!/usr/bin/env bash
#
# Generate a jibri.conf from a docker-jitsi-meet .env and run jibri from
# this source tree, connecting it to the running docker deployment.
#
# Defaults:
#   JIBRI_DIR        -> repo root (resolved from this script's location)
#   DOCKER_JITSI_DIR -> $HOME/dev/docker-jitsi-meet
#
# Pre-req: prosody port 5222 must be reachable from the host. The default
# docker-jitsi-meet compose only `expose:`s it, so apply prosody.override.yml
# from this directory:
#
#   docker compose -f docker-compose.yml \
#                  -f /path/to/jibri/doc/local-run/prosody.override.yml up -d
#
# See README.md alongside this script for a full walkthrough.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JIBRI_DIR="${JIBRI_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
DOCKER_JITSI_DIR="${DOCKER_JITSI_DIR:-$HOME/dev/docker-jitsi-meet}"
ENV_FILE="$DOCKER_JITSI_DIR/.env"

[ -f "$ENV_FILE" ] || { echo "missing $ENV_FILE (set DOCKER_JITSI_DIR)" >&2; exit 1; }
[ -d "$JIBRI_DIR" ] || { echo "missing $JIBRI_DIR" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

# Defaults match jibri/rootfs/defaults/xmpp.conf and jibri.conf templates.
XMPP_DOMAIN="${XMPP_DOMAIN:-meet.jitsi}"
XMPP_AUTH_DOMAIN="${XMPP_AUTH_DOMAIN:-auth.meet.jitsi}"
XMPP_INTERNAL_MUC_DOMAIN="${XMPP_INTERNAL_MUC_DOMAIN:-internal-muc.meet.jitsi}"
XMPP_HIDDEN_DOMAIN="${XMPP_HIDDEN_DOMAIN:-hidden.meet.jitsi}"
XMPP_MUC_DOMAIN="${XMPP_MUC_DOMAIN:-muc.meet.jitsi}"
JIBRI_BREWERY_MUC="${JIBRI_BREWERY_MUC:-jibribrewery}"
JIBRI_RECORDER_USER="${JIBRI_RECORDER_USER:-recorder}"
JIBRI_XMPP_USER="${JIBRI_XMPP_USER:-jibri}"
JIBRI_USAGE_TIMEOUT="${JIBRI_USAGE_TIMEOUT:-0}"
XMPP_PORT="${XMPP_PORT:-5222}"
XMPP_HOST="${XMPP_HOST:-127.0.0.1}"
JIBRI_RECORDING_RESOLUTION="${JIBRI_RECORDING_RESOLUTION:-1280x720}"
JIBRI_AUDIO_SOURCE="${JIBRI_AUDIO_SOURCE:-alsa}"
JIBRI_AUDIO_DEVICE="${JIBRI_AUDIO_DEVICE:-plug:bsnoop}"
JIBRI_INSTANCE_ID="${JIBRI_INSTANCE_ID:-jibri-local-$$}"
JIBRI_STRIP_DOMAIN_JID="${JIBRI_STRIP_DOMAIN_JID:-${XMPP_MUC_DOMAIN%%.*}}"

: "${JIBRI_XMPP_PASSWORD:?JIBRI_XMPP_PASSWORD not set in .env (run gen-passwords.sh)}"
: "${JIBRI_RECORDER_PASSWORD:?JIBRI_RECORDER_PASSWORD not set in .env}"

BASE_URL_LINE=""
[ -n "${PUBLIC_URL:-}" ] && BASE_URL_LINE="          base-url = \"$PUBLIC_URL\""

WORKDIR="$JIBRI_DIR/.local"
JIBRI_CONF="$WORKDIR/jibri.conf"
LOGGING="$WORKDIR/logging.properties"
RECORDINGS="$WORKDIR/recordings"
mkdir -p "$WORKDIR" "$RECORDINGS"

# Probe prosody on the host before generating config / starting jibri.
# Run the /dev/tcp probe in a subshell so neither fd 3 nor stderr leak back here.
if ! (exec 3<>"/dev/tcp/$XMPP_HOST/$XMPP_PORT") 2>/dev/null; then
  cat >&2 <<EOF
cannot reach prosody at $XMPP_HOST:$XMPP_PORT.

The default compose only exposes 5222 inside the docker network. Apply the
override shipped next to this script and restart:

  docker compose -f docker-compose.yml -f $SCRIPT_DIR/prosody.override.yml up -d
EOF
  exit 1
fi

cat > "$JIBRI_CONF" <<EOF
jibri {
  id = "$JIBRI_INSTANCE_ID"
  single-use-mode = false

  recording {
    recordings-directory = "$RECORDINGS"
    finalize-script = "$JIBRI_DIR/resources/finalize_recording.sh"
  }

  ffmpeg {
    resolution = "$JIBRI_RECORDING_RESOLUTION"
    audio-source = "$JIBRI_AUDIO_SOURCE"
    audio-device = "$JIBRI_AUDIO_DEVICE"
  }

  chrome {
    flags = [
      "--use-fake-ui-for-media-stream",
      "--enabled",
      "--autoplay-policy=no-user-gesture-required",
      "--ignore-certificate-errors"
    ]
  }

  stats {
    prometheus.enabled = false
  }

  api {
    xmpp {
      environments = [
        {
          name = "docker-local"
          xmpp-server-hosts = ["$XMPP_HOST"]
          xmpp-domain = "$XMPP_DOMAIN"
$BASE_URL_LINE

          control-muc {
            domain = "$XMPP_INTERNAL_MUC_DOMAIN"
            room-name = "$JIBRI_BREWERY_MUC"
            nickname = "$JIBRI_INSTANCE_ID"
          }

          control-login {
            domain = "$XMPP_AUTH_DOMAIN"
            port = "$XMPP_PORT"
            username = "$JIBRI_XMPP_USER"
            password = "$JIBRI_XMPP_PASSWORD"
          }

          call-login {
            domain = "$XMPP_HIDDEN_DOMAIN"
            username = "$JIBRI_RECORDER_USER"
            password = "$JIBRI_RECORDER_PASSWORD"
          }

          strip-from-room-domain = "$JIBRI_STRIP_DOMAIN_JID."
          usage-timeout = "$JIBRI_USAGE_TIMEOUT"
          trust-all-xmpp-certs = true
        }
      ]
    }
  }
}
EOF

# Console-only logging. Root .level must allow FINE through to the handler.
cat > "$LOGGING" <<EOF
handlers = java.util.logging.ConsoleHandler

.level = FINE

java.util.logging.ConsoleHandler.level = FINE
java.util.logging.ConsoleHandler.formatter = org.jitsi.utils.logging2.JitsiLogFormatter

# ffmpeg/browser are attached to dedicated JUL loggers with parent propagation
# disabled, so their output never reaches the console. Pin their FileHandlers
# to the workdir so we can read them.
org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler.pattern = $WORKDIR/ffmpeg.%g.txt
org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler.formatter = org.jitsi.utils.logging2.JitsiLogFormatter
org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler.limit = 10000000
org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler.count = 5

org.jitsi.jibri.selenium.util.BrowserFileHandler.pattern = $WORKDIR/browser.%g.txt
org.jitsi.jibri.selenium.util.BrowserFileHandler.formatter = org.jitsi.utils.logging2.JitsiLogFormatter
org.jitsi.jibri.selenium.util.BrowserFileHandler.limit = 10000000
org.jitsi.jibri.selenium.util.BrowserFileHandler.count = 5

org.jitsi.level = FINE
org.jitsi.jibri.config.level = INFO
org.glassfish.level = INFO
EOF

JAR="$(ls -t "$JIBRI_DIR"/target/jibri-*-jar-with-dependencies.jar 2>/dev/null | head -n1 || true)"
if [ -z "$JAR" ]; then
  echo "building jibri..."
  (cd "$JIBRI_DIR" && mvn -B -DskipTests clean package)
  JAR="$(ls -t "$JIBRI_DIR"/target/jibri-*-jar-with-dependencies.jar | head -n1)"
fi

CHROMEDRIVER="${CHROMEDRIVER:-$JIBRI_DIR/chromedriver}"
[ -x "$CHROMEDRIVER" ] || { echo "missing chromedriver at $CHROMEDRIVER" >&2; exit 1; }

echo "config:       $JIBRI_CONF"
echo "jar:          $JAR"
echo "chromedriver: $CHROMEDRIVER"
echo "xmpp:         $XMPP_HOST:$XMPP_PORT  (domain=$XMPP_DOMAIN)"
echo

exec java \
  -Djava.util.logging.config.file="$LOGGING" \
  -Dconfig.file="$JIBRI_CONF" \
  -Dwebdriver.chrome.driver="$CHROMEDRIVER" \
  -Dwebdriver.chrome.logfile="$WORKDIR/chromedriver.log" \
  -jar "$JAR"
