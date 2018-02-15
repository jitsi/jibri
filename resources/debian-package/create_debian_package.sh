#!/bin/bash

JIBRI_JAR_PATH=$1
echo "Copying $JIBRI_JAR_PATH into package location"

# Copy the built jar into the expected location
cp $JIBRI_JAR_PATH jibri/opt/jitsi/jibri/

# Build the package
dpkg-deb --build jibri
