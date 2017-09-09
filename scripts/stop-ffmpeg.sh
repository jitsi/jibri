#!/bin/bash
PID_DIR=/var/run/jibri/
[ -e "$PID_DIR/ffmpeg.pid" ] && kill -2 `cat $PID_DIR/ffmpeg.pid`
