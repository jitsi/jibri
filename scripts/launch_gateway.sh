#!/bin/bash

# SCRIPT TO LAUNCH PJSUA FROM PARAMETERS
#Directory for storing pids (should be writeable by jibri user)
PID_DIR=/var/run/jibri/


SIP_ADDRESS=$1

screen -S pjsua -d -m ./start-pjsua.sh $SIP_ADDRESS
sleep 1
PID=$(pidof pjsua)
[ ! -z "$PID" ] && echo $PID > $PID_DIR/pjsua.pid 