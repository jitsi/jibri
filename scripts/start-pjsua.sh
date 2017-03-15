#!/bin/bash

# SCRIPT TO LAUNCH PJSUA FROM PARAMETERS


SIP_ADDRESS=$1
DISPLAY_NAME="${2:-Meeting Room}"

CONFIG_FILE=/home/jibri/pjsua.config

CAPTURE_DEV=23
PLAYBACK_DEV=24

PID_DIR=/var/run/jibri/
LOG_FILE=/tmp/jibri-pjsua.log
EXIT_FILE=/tmp/jibri-pjsua.result

#clear out the exit code if there's a leftover file
[ -e "$EXIT_FILE" ] && rm $EXIT_FILE

export DISPLAY=:1

pjsua \
    --capture-dev=$CAPTURE_DEV \
    --playback-dev=$PLAYBACK_DEV \
    --id "$DISPLAY_NAME <sip:jibri@127.0.0.1>" \
    --config-file $CONFIG_FILE \
    --log-file=$LOG_FILE \
    sip:$SIP_ADDRESS

RETURN=$?

echo $RETURN > $EXIT_FILE
