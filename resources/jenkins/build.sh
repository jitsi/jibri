#!/bin/sh

set -e

cd $WORKSPACE/
mvn clean verify package
