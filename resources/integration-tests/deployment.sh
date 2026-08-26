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
#   DJM_VERSION  image tag to run (default: unstable, which is what jibri master targets)
#   DJM_CONFIG   host directory for the generated container config.  It is owned by this script and
#                wiped on every 'up', so point it at a dedicated directory.
#   HTTPS_PORT   port the deployment is served on (default: 8443)

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

DJM_DIR="${DJM_DIR:-$PROJECT_DIR/target/docker-jitsi-meet}"
DJM_BRANCH="${DJM_BRANCH:-master}"
DJM_VERSION="${DJM_VERSION:-unstable}"
DJM_CONFIG="${DJM_CONFIG:-$PROJECT_DIR/target/jitsi-meet-cfg}"
HTTP_PORT="${HTTP_PORT:-8000}"
HTTPS_PORT="${HTTPS_PORT:-8443}"
JVB_PORT="${JVB_PORT:-10000}"
JICOFO_REST_PORT="${JICOFO_REST_PORT:-8888}"
JVB_COLIBRI_PORT="${JVB_COLIBRI_PORT:-8080}"

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
    # The containers create files as root, which the user running the tests cannot remove.
    rm -rf "$DJM_CONFIG" 2>/dev/null || sudo rm -rf "$DJM_CONFIG"
}

write_env() {
    mkdir -p "$DJM_CONFIG"/{web,jicofo,jvb} \
        "$DJM_CONFIG"/prosody/{config,prosody-plugins-custom} \
        "$DJM_CONFIG"/storage/{web,transcripts,prosody} \
        "$DJM_CONFIG"/tmp/web-load-test
    # The containers run as uid 1000 and refuse to start if they cannot write to their volumes, and the
    # user running the tests is not uid 1000 on every host.
    chmod -R 777 "$DJM_CONFIG"

    # hiddenFromRecorderFeatureEnabled is not one of the settings jitsi-meet lets a url override, so the
    # deployment has to turn it on.  Without it lib-jitsi-meet drops hidden-from-recorder from the identity
    # and jibri can never see that a participant is hidden from it.
    cat > "$DJM_CONFIG/web/custom-config.js" <<'CONFIGEOF'
config.hiddenFromRecorderFeatureEnabled = true;
CONFIGEOF

    echo "Using JVB_ADVERTISE_IPS=$JVB_ADVERTISE_IPS"

    cat > "$ENV_FILE" <<ENVEOF
CONFIG=$DJM_CONFIG
JITSI_IMAGE_VERSION=$DJM_VERSION
HTTP_PORT=$HTTP_PORT
HTTPS_PORT=$HTTPS_PORT
JVB_PORT=$JVB_PORT
JICOFO_REST_PORT=$JICOFO_REST_PORT
JVB_COLIBRI_PORT=$JVB_COLIBRI_PORT
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
# Exposes jicofo's metrics, which the readiness check below polls.
JICOFO_ENABLE_REST=1
# Accept JWTs so that participants can join with an identity, which is what jibri reports from
# getParticipants and what marks somebody as hidden from the recorder.  Empty tokens stay allowed so
# that jibri, and any participant that does not need an identity, can still join anonymously; and
# jicofo keeps handing moderator rights to the first participant, which the tests that kick jibri or
# turn on AV moderation rely on.  The credentials are fixed and known to the tests.
ENABLE_AUTH=1
AUTH_TYPE=jwt
JWT_APP_ID=jibri-integration-tests
JWT_APP_SECRET=jibri-integration-tests-secret
JWT_ACCEPTED_ISSUERS=jibri-integration-tests
JWT_ACCEPTED_AUDIENCES=jibri-integration-tests
JWT_ALLOW_EMPTY=1
JICOFO_ENABLE_AUTH=0
# Puts the identity from the token into the participant's presence.  This one hooks the virtual host
# rather than the muc component, so it belongs in XMPP_MODULES rather than XMPP_MUC_MODULES.
XMPP_MODULES=presence_identity
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

# The web container serving pages is not enough: a conference cannot be created until jicofo and the jvb
# have registered with prosody, and the first test would otherwise race them.
wait_for_deployment() {
    echo "Waiting for the deployment to come up on https://localhost:$HTTPS_PORT"
    for _ in $(seq 1 90); do
        if curl -ksf -o /dev/null "https://localhost:$HTTPS_PORT/external_api.js" &&
            curl -sf -o /dev/null "http://localhost:$JVB_COLIBRI_PORT/about/health" &&
            # jicofo only reports per-bridge metrics once it has seen a jvb join the brewery.
            curl -sf "http://localhost:$JICOFO_REST_PORT/metrics" | grep -q '^jitsi_jicofo_bridge_.*jvb='; then
            echo "Deployment is up"
            return 0
        fi
        sleep 2
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
        # The image tags the tests use float, and 'up' on its own reuses whatever was pulled last time,
        # so a local checkout quietly drifts behind what CI runs against.
        compose pull --quiet
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
