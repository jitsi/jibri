#!/bin/bash
PID_DIR=/var/run/jibri/

kill `cat $PID_DIR/ffmpeg.pid`
sleep 5
killall chrome
killall chromedriver
rm ${PID_DIR}/*
