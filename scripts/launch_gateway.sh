#!/bin/bash

# SCRIPT TO LAUNCH PJSUA FROM PARAMETERS


SIP_ADDRESS=$1

PID_DIR=/var/run/jibri/

CAPTURE_DEV=23
PLAYBACK_DEV=24
CONFIG_FILE=/home/jibri/pjsua.config

export DISPLAY=:1

pjsua --capture-dev=$CAPTURE_DEV --playback-dev=$PLAYBACK_DEV --config-file $CONFIG_FILE sip:$SIP_ADDRESS > /tmp/jibri-pjsua.out 2>&1 &
echo $! > $PID_DIR/pjsua.pid 
