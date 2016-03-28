#!/bin/sh
source config
python3 app.py -b $CHROME_BINARY -t $TIMEOUT -u $URL -j $JID -p $PASS -r $ROOM -n $NICK -P $ROOMPASS $SERVERS -d
