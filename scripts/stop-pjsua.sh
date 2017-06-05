#!/bin/bash
[ -z "$PID_DIR" ] && PID_DIR="/var/run/jibri"
[ -e "$PID_DIR/pjsua.pid" ] && kill `cat $PID_DIR/pjsua.pid`
