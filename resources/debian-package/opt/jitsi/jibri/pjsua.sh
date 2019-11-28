#!/bin/bash

exec /usr/local/bin/pjsua --config-file /etc/jitsi/jibri/pjsua.config "$@" > /dev/null
