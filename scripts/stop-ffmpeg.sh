#!/bin/bash
PID_DIR=/var/run/jibri/
[ -e "$PID_DIR/ffmpeg.pid" ] && kill `cat $PID_DIR/ffmpeg.pid`
