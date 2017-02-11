#!/bin/bash
PID_DIR=/var/run/jibri/
./stop-ffmpeg.sh
./stop_selenium.sh
sleep 1
[ -e "${PID_DIR}/ffmpeg.pid" ] && rm ${PID_DIR}/ffmpeg.pid
