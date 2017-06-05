#!/bin/bash
[ -z "$PID_DIR" ] && PID_DIR="/var/run/jibri"
./stop-ffmpeg.sh
./stop-pjsua.sh
./stop_selenium.sh
sleep 1
[ -e "${PID_DIR}/ffmpeg.pid" ] && rm ${PID_DIR}/ffmpeg.pid
[ -e "${PID_DIR}/pjsua.pid" ] && rm ${PID_DIR}/pjsua.pid
