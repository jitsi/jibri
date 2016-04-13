#!/bin/sh
source config
set -x
[ -z "$JID" ] || JID_FLAG="-j $JID"
[ -z "$URL" ] || URL_FLAG="-u $URL"
[ -z "$PASS" ] || PASS_FLAG="-p $PASS"
[ -z "$ROOM" ] || ROOM_FLAG="-r $ROOM"
[ -z "$NICK" ] || NICK_FLAG="-n $NICK"
[ -z "$CHROME_BINARY" ] || CHROME_FLAG="-b $CHROME_BINARY"
[ -z "$TIMEOUT" ] || TIMEOUT_FLAG="-t $TIMEOUT"
[ -z "$ROOMPASS" ] || ROOMPASS_FLAG="-P $ROOMPASS"

python3 app.py -d $CHROME_FLAG $TIMEOUT_FLAG $ROOMPASS_FLAG $URL_FLAG $JID_FLAG $PASS_FLAG $ROOM_FLAG $NICK_FLAG $SERVERS
