#!/bin/bash

CONF="/etc/jitsi/jibri/jibri.conf"

PORT=$(hocon -f $CONF get jibri.api.http.internal-api-port 2>/dev/null || true)
[[ -z "$PORT" ]] && PORT=3333

curl -sX POST http://127.0.0.1:$PORT/jibri/api/internal/v1.0/shutdown
