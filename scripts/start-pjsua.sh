#!/bin/bash

# SCRIPT TO LAUNCH PJSUA FROM PARAMETERS


SIP_ADDRESS=$1
DISPLAY_NAME="${2:-Meeting Room}"

CONFIG_FILE=/home/jibri/pjsua.config

CAPTURE_DEV=23
PLAYBACK_DEV=24

PID_DIR=/var/run/jibri/
LOG_FILE=/tmp/jibri-pjsua.log


export DISPLAY=:1

pjsua \
    --capture-dev=$CAPTURE_DEV \
    --playback-dev=$PLAYBACK_DEV \
    --id "$DISPLAY_NAME <sip:jibri@127.0.0.1>" \
    --config-file $CONFIG_FILE
    --log-file=$LOG_FILE \
    sip:$SIP_ADDRESS

RETURN=$?

if [ "$RETURN" -eq 0 ]; then
    #graceful exit, so don't write to error
else
    #do something to restart?
fi
