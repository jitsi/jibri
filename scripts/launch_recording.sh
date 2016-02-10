#!/bin/bash
#Directory for storing pids (should be writeable by jibri user)
PID_DIR=/var/run/jibri/

URL=$1
STREAM=$2
TOKEN=$3
YOUTUBE_STREAM_ID=$4

export DISPLAY=:0

#launch xorg and wait
Xorg -noreset  +extension RANDR +extension RENDER -logfile ./xorg.log  -config ./xorg-video-dummy.conf :0 > /tmp/jibri-xorg.out 2>&1 &
echo $! > $PID_DIR/Xorg.pid
sleep 1

#launch a window manager and wait
icewm-session > /tmp/jibri-icewm.out 2>&1 &
echo $! > $PID_DIR/icewm.pid
sleep 1

#launch chrome in selenium in the appropriate room
# TODO: maybe include this as a module in the control app, so we can monitor it
# easier and send errors to jicofo if it bails.
python3 -i start-chromium-selenium.py -u "$URL" -t $TOKEN > /tmp/jibri-python-selenium.out 2>&1 &
echo $! > $PID_DIR/python-selenium.pid
sleep 1


YOUTUBE_BASE="rtmp://a.rtmp.youtube.com/live2"
if [ ! -z "$4" ]; then
    STREAM="${YOUTUBE_BASE}/${YOUTUBE_STREAM_ID}"
fi

./start-ffmpeg.sh "$STREAM"
echo $! > $PID_DIR/ffmpeg.pid 
