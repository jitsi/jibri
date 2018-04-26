#!/bin/sh

set -e

cd $WORKSPACE/
mvn verify
mvn package
