#!/bin/bash
#
# Brings up (or tears down) a docker-jitsi-meet deployment for the jibri
# integration tests.  Used both by the GitHub workflow and locally.
#
# Usage:
#   resources/integration-tests/deployment.sh up
#   resources/integration-tests/deployment.sh down
#   resources/integration-tests/deployment.sh logs
#
# Environment:
#   DJM_DIR      where docker-jitsi-meet is checked out (it is cloned if missing)
#   DJM_BRANCH   branch to clone (default: master)
#   DJM_VERSION  image tag to run (default: stable)
#   DJM_CONFIG   host directory for the generated container config.  It is owned by this script and
#                wiped on every 'up', so point it at a dedicated directory.
#   HTTPS_PORT   port the deployment is served on (default: 8443)

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

DJM_DIR="${DJM_DIR:-$PROJECT_DIR/target/docker-jitsi-meet}"
DJM_BRANCH="${DJM_BRANCH:-master}"
DJM_VERSION="${DJM_VERSION:-stable}"
DJM_CONFIG="${DJM_CONFIG:-$PROJECT_DIR/target/jitsi-meet-cfg}"
HTTP_PORT="${HTTP_PORT:-8000}"
HTTPS_PORT="${HTTPS_PORT:-8443}"
JVB_PORT="${JVB_PORT:-10000}"

ENV_FILE="$DJM_CONFIG/.env"

# The browsers running the tests live on the host and the jvb's media port is published to the host, so
# the jvb advertises loopback: the host reaches the container on 127.0.0.1 whether docker runs natively or
# in a VM.  Override JVB_ADVERTISE_IPS to run the browsers somewhere else.
JVB_ADVERTISE_IPS="${JVB_ADVERTISE_IPS:-127.0.0.1}"

compose() {
    docker compose \
        --project-directory "$DJM_DIR" \
        --project-name jibri-integration-tests \
        --env-file "$ENV_FILE" \
        -f "$DJM_DIR/docker-compose.yml" \
        "$@"
}

checkout() {
    if [ ! -d "$DJM_DIR" ]; then
        echo "Cloning docker-jitsi-meet ($DJM_BRANCH) into $DJM_DIR"
        git clone --depth 1 --branch "$DJM_BRANCH" \
            https://github.com/jitsi/docker-jitsi-meet.git "$DJM_DIR"
    fi
}

# The container config is generated on first start and keeps the passwords the components
# authenticate with, so reusing a config directory with a freshly generated .env leaves jicofo and the
# jvb unable to log in to prosody.  Always start from an empty one.
reset_config() {
    case "$DJM_CONFIG" in
        "" | "/" | "$HOME")
            echo "Refusing to wipe DJM_CONFIG=$DJM_CONFIG" >&2
            exit 1
            ;;
    esac
    rm -rf "$DJM_CONFIG"
}

write_env() {
    mkdir -p "$DJM_CONFIG"/{web,transcripts,prosody/config,prosody/prosody-plugins-custom,jicofo,jvb}

    echo "Using JVB_ADVERTISE_IPS=$JVB_ADVERTISE_IPS"

    cat > "$ENV_FILE" <<ENVEOF
CONFIG=$DJM_CONFIG
JITSI_IMAGE_VERSION=$DJM_VERSION
HTTP_PORT=$HTTP_PORT
HTTPS_PORT=$HTTPS_PORT
JVB_PORT=$JVB_PORT
JVB_ADVERTISE_IPS=$JVB_ADVERTISE_IPS
# The generated jitsi-meet config hardcodes https/wss for the bosh and websocket urls, so the
# deployment has to be reached over https.  It serves a self-signed certificate, which the tests tell
# their browsers to accept.
PUBLIC_URL=https://localhost:$HTTPS_PORT
TZ=UTC
RESTART_POLICY=no
ENABLE_LOBBY=1
ENABLE_PREJOIN_PAGE=0
ENABLE_P2P=0
ENABLE_COLIBRI_WEBSOCKET=1
ENABLE_XMPP_WEBSOCKET=1
JICOFO_AUTH_PASSWORD=$(openssl rand -hex 16)
JVB_AUTH_PASSWORD=$(openssl rand -hex 16)
JIGASI_XMPP_PASSWORD=$(openssl rand -hex 16)
JIGASI_TRANSCRIBER_PASSWORD=$(openssl rand -hex 16)
JIBRI_RECORDER_PASSWORD=$(openssl rand -hex 16)
JIBRI_XMPP_PASSWORD=$(openssl rand -hex 16)
ENVEOF
}

teardown() {
    [ -f "$ENV_FILE" ] || return 0
    compose down -v --remove-orphans
}

wait_for_deployment() {
    echo "Waiting for the deployment to come up on https://localhost:$HTTPS_PORT"
    for _ in $(seq 1 60); do
        if curl -ksf -o /dev/null "https://localhost:$HTTPS_PORT/external_api.js"; then
            echo "Deployment is up"
            return 0
        fi
        sleep 5
    done
    echo "Deployment did not come up in time" >&2
    compose ps
    compose logs --tail 100
    exit 1
}

case "${1:-up}" in
    up)
        checkout
        teardown
        reset_config
        write_env
        compose up -d
        wait_for_deployment
        ;;
    down)
        teardown
        reset_config
        ;;
    logs)
        compose logs --tail "${2:-200}"
        ;;
    *)
        echo "Usage: $0 up|down|logs" >&2
        exit 1
        ;;
esac
