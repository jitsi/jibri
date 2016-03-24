#!/bin/sh
source config
python3 app.py -u $URL -j $JID -p $PASS -r $ROOM -n $NICK -P $ROOMPASS $SERVERS -d
