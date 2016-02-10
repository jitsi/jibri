#!/bin/bash

STREAM=$1

RESOLUTION="1280x720"
RATE=30
PRESET=veryfast
YOUTUBE_BASE="rtmp://a.rtmp.youtube.com/live2"
QUEUE_SIZE=4096

DISPLAY=:0

#Record the output of display :0 plus the ALSA loopback device hw:0,1,0
exec ffmpeg -y -v info \
    -f x11grab -r $RATE -s $RESOLUTION -thread_queue_size $QUEUE_SIZE -i :0.0+0,0 \
    -f alsa -thread_queue_size $QUEUE_SIZE -i 'hw:0,1,0' -acodec libmp3lame -ar 44100 \
    -c:v libx264 -preset $PRESET -maxrate 1984k -bufsize 3968k -pix_fmt yuv420p -r 30 \
    -crf 28 -g 60  -tune zerolatency \
    -f flv $STREAM > /tmp/jibri-ffmpeg.out 2>&1 &
