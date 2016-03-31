#!/bin/bash
PID_DIR=/var/run/jibri/
./stop-ffmpeg.sh
sleep 1
killall chrome
killall chromedriver
rm ${PID_DIR}/*.pid
