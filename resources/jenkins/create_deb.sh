#!/bin/bash

JAR_LOCATION=../../target/jibri-1.0-SNAPSHOT-jar-with-dependencies.jar

cd $WORKSPACE/resources/debian-package

./create_debian_package.sh $JAR_LOCATION
