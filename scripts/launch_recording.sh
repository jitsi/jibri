#!/bin/bash

URL=$1
STREAM=$2
TOKEN=$3
YOUTUBE_STREAM_ID=$4


[ -z "$DISPLAY" ] && DISPLAY=":0"
export DISPLAY

xdotool mousemove 1280 0

YOUTUBE_BASE="rtmp://a.rtmp.youtube.com/live2"


if [ ! -z "$5" ]; then 
    YOUTUBE_BASE="rtmp://b.rtmp.youtube.com/live2"
fi

if [ ! -z "$4" ]; then
    STREAM="${YOUTUBE_BASE}/${YOUTUBE_STREAM_ID}"

    if [ ! -z "$5" ]; then 
        STREAM="${STREAM}?backup=1"
    fi
fi

./start-ffmpeg.sh $STREAM
