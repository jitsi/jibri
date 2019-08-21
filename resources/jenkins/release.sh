#!/bin/bash
set -e

# This script should be run after the build/tests have successfully completed

# Prune any local tags that aren't on the remote
git fetch --prune origin "+refs/tags/*:refs/tags/*"

cd $WORKSPACE

# Let's get version from maven
MVNVER=`xmllint --xpath "/*[local-name()='project']/*[local-name()='version']/text()" pom.xml`
TAG_NAME="v${MVNVER/-SNAPSHOT/}"
echo "Current tag name: $TAG_NAME"

if ! git rev-parse $TAG_NAME >/dev/null 2>&1
then
    git tag -a $TAG_NAME -m "Tagged automatically by Jenkins"
	  git push origin $TAG_NAME
else
	echo "Tag: $TAG_NAME already exists."
fi

VERSION_FULL=`git describe --match "v[0-9\.]*" --long`
echo "Full version: ${VERSION_FULL}"

VERSION=${VERSION_FULL:1}
echo "Package version: ${VERSION}"

REV=$(git log --pretty=format:'%h' -n 1)

# bulding the debian package expects the file target/jibri.jar
mv target/jibri-${MVNVER}-jar-with-dependencies.jar target/jibri.jar

dch -v "${VERSION}-1" "Built from git. $REV"
dch -D unstable -r ""

dpkg-buildpackage -A -rfakeroot -us -uc
