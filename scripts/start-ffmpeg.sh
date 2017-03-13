#!/bin/bash

STREAM=$1

: ${RESOLUTION:=1280x720}
: ${RATE:=30}
: ${PRESET:=fast}
: ${YOUTUBE_BASE:="rtmp://a.rtmp.youtube.com/live2"}
: ${QUEUE_SIZE:=4096}

#use alsa directly
: ${INPUT_DEVICE:='plug:jibri'}
: ${MAX_BITRATE:='2976'}
: ${BUFSIZE:=$(($MAX_BITRATE * 2))}
: ${CRF:=25}
: ${G:=$(($RATE * 2))}
#use pulse for audio input
#INPUT_DEVICE='pulse'


DISPLAY=:0

#Record the output of display :0 plus the ALSA loopback device plug:jibri
exec ffmpeg -y -v info \
    -f x11grab -draw_mouse 0 -r $RATE -s $RESOLUTION -thread_queue_size $QUEUE_SIZE -i :0.0+0,0 \
    -f alsa -thread_queue_size $QUEUE_SIZE -i $INPUT_DEVICE -acodec libmp3lame -ar 44100 \
    -c:v libx264 -preset $PRESET -maxrate ${MAX_BITRATE}k -bufsize ${BUFSIZE}k -pix_fmt yuv420p -r $RATE \
    -crf $CRF -g $G  -tune zerolatency \
    -f flv $STREAM > /tmp/jibri-ffmpeg.out 2>&1 &
