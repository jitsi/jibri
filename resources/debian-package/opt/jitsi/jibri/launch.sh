#!/bin/bash

exec java -Djava.util.logging.config.file=/etc/jitsi/jibri/logging.properties -Dconfig.file="/etc/jitsi/jibri/jibri.conf" -jar /opt/jitsi/jibri/jibri.jar --config "/etc/jitsi/jibri/config.json"
