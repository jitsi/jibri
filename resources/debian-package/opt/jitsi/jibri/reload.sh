#!/bin/bash

CONF="/etc/jitsi/jibri/jibri.conf"
PORT=$(egrep -so "^\s*internal-api-port\s*=\s*[0-9]+" $CONF | \
    egrep -so "[0-9]+" | tail -1)
[[ -z "$PORT" ]] && PORT=3333

curl -X POST http://127.0.0.1:$PORT/jibri/api/internal/v1.0/notifyConfigChanged
