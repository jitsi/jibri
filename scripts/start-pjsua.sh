#!/bin/bash

# SCRIPT TO LAUNCH PJSUA FROM PARAMETERS


SIP_ADDRESS=$1

CONFIG_FILE=/home/jibri/pjsua.config

CAPTURE_DEV=23
PLAYBACK_DEV=24

PID_DIR=/var/run/jibri/
LOG_FILE=/tmp/jibri-pjsua.log


export DISPLAY=:1

pjsua --capture-dev=$CAPTURE_DEV --playback-dev=$PLAYBACK_DEV --config-file $CONFIG_FILE sip:$SIP_ADDRESS --log-file=$LOG_FILE
RETURN=$?

if [ "$RETURN" -eq 0 ]; then
    #graceful exit, so don't write to error
else
    #do something to restart?
fi