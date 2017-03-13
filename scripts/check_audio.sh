#!/bin/bash
#fail if anything goes wrong in the meantime
set -e

INPUT_DEVICE='plug:jibri'
OUTPUT_FILE='/tmp/jibri-audio_check.wav'
LOGFILE='/tmp/jibri-audio_check.log'
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
