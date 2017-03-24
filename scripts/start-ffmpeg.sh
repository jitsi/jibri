#!/bin/bash

PID_DIR=/var/run/jibri/

STREAM=$1

: ${RESOLUTION:=1280x720}
: ${FRAMERATE:=30}
: ${PRESET:=veryfast}
: ${QUEUE_SIZE:=4096}

#use alsa directly
: ${INPUT_DEVICE:='hw:0,1,0'}
: ${MAX_BITRATE:='2976'}
: ${BUFSIZE:=$(($MAX_BITRATE * 2))}
: ${CRF:=25}
: ${G:=$(($FRAMERATE * 2))}
#use pulse for audio input
#INPUT_DEVICE='pulse'

DISPLAY=:0

#Record the output of display :0 plus the ALSA loopback device hw:0,1,0
ffmpeg -y -v info -f x11grab -draw_mouse 0 -r $FRAMERATE -s $RESOLUTION -thread_queue_size $QUEUE_SIZE -i ${DISPLAY}.0+0,0 \
    -f alsa -thread_queue_size $QUEUE_SIZE -i $INPUT_DEVICE  -acodec libmp3lame -ar 44100 \
    -c:v libx264 -preset $PRESET -maxrate ${MAX_BITRATE}k -bufsize ${BUFSIZE}k -pix_fmt yuv420p -r $FRAMERATE -crf $CRF -g $G  -tune zerolatency \
    -f flv $STREAM > /tmp/jibri-ffmpeg.out 2>&1 &
echo $! > $PID_DIR/ffmpeg.pid
