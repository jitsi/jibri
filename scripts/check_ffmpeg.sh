#!/bin/bash
OUTPUT_PATH=$1

[ -z "$OUTPUT_PATH" ] && OUTPUT_PATH="/tmp/jibri-ffmpeg.out"

if [ ! -e "$OUTPUT_PATH" ]; then
    echo "No file found $OUTPUT_PATH"
    exit 128
fi

#look for frame= on the last line of the file
cat $OUTPUT_PATH | tr \\r \\n | tail -10 | grep -q frame=
exit $?