#!/bin/bash
#fail if anything goes wrong in the meantime
set -e

[ -z "$OUTPUT_DIR" ] && OUTPUT_DIR="/tmp"

INPUT_DEVICE='hw:0,1'
OUTPUT_FILE="$OUTPUT_DIR/jibri-audio_check.wav"
LOGFILE="/$OUTPUT_DIR/jibri-audio_check.log"
RECORD_TIME=1

RECORD_BIN='/usr/bin/arecord'
XXD_BIN='/usr/bin/xxd'

$RECORD_BIN -d $RECORD_TIME -D $INPUT_DEVICE -v -f cd -t raw $OUTPUT_FILE > $LOGFILE 2>&1
SIGNAL_COUNT=$($XXD_BIN $OUTPUT_FILE | grep -v ' 0000 ' | wc -l)
if [ $SIGNAL_COUNT -gt 0 ]; then    
    exit 0
else
    exit 1
fi
rm $OUTPUT_FILE
