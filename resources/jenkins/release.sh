#!/bin/bash

# This script should be run after the build/tests have successfully completed

MAJOR_MINOR=$1

DESCRIBE=`git describe --tags --always`
REV=$(git log --pretty=format:'%h' -n 1)

CURR_VERSION=`echo $DESCRIBE | awk '{split($0,a,"-"); print a[1]}'`
echo "Got current git version $CURR_VERSION"
MAJOR_VERSION=`echo $CURR_VERSION | awk '{split($0,a,"."); print a[1]}'`
MINOR_VERSION=`echo $CURR_VERSION | awk '{split($0,a,"."); print a[2]}'`

if [ "$MAJOR_MINOR" == "Minor" ]; then
    ((MINOR_VERSION++))
elif [ "$MAJOR_MINOR" == "Major" ]; then
    ((MAJOR_VERSION++))
    ((MINOR_VERSION=0))
else
    echo "Error, unrecognized release type $MAJOR_MINOR.  Options are 'Major' and 'Minor'"
    exit 1
fi

echo "New version will be $MAJOR_VERSION.$MINOR_VERSION"

cd $WORKSPACE

git tag -a $MAJOR_VERSION.$MINOR_VERSION -m "New $MAJOR_MINOR release"

dch -v "$MAJOR_VERSION.$MINOR_VERSION-1" "Built from git. $REV"
dch -D unstable -r ""

dpkg-buildpackage -A -rfakeroot -us -uc

cp $WORKSPACE/../jibri_$MAJOR_VERSION.$MINOR_VERSION* $WORKSPACE

#TODO: push tag to remote
