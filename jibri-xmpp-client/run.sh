#!/bin/sh
source config
python3 app.py -j $JID -p $PASS -r $ROOM -n $NICK -P $ROOMPASS $SERVERS -d
