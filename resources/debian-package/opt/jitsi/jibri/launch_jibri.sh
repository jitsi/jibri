#!/bin/bash

java -Djava.util.logging.config.file=/etc/jitsi/jibri/logging.properties -jar /opt/jitsi/jibri/jibri.jar --config "/etc/jitsi/jibri/config.json"
