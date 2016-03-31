#!/bin/sh
source config
set -x
[ -z "$CHROME_BINARY" ] || CHROME_FLAG="-b $CHROME_BINARY"
[ -z "$TIMEOUT" ] || TIMEOUT_FLAG="-t $TIMEOUT"
[ -z "$ROOMPASS" ] || ROOMPASS_FLAG="-P $ROOMPASS"

python3 app.py $CHROME_FLAG $TIMEOUT_FLAG $ROOMPASS_FLAG -u $URL -j $JID -p $PASS -r $ROOM -n $NICK $SERVERS -d
