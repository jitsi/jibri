#!/bin/sh
source config
python3 app.py -t $TIMEOUT -u $URL -j $JID -p $PASS -r $ROOM -n $NICK -P $ROOMPASS $SERVERS -d
