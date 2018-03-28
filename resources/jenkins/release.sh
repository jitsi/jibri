#!/bin/bash
set -e

# This script should be run after the build/tests have successfully completed

MAJOR_MINOR=$1

# Prune any local tags that aren't on the remote
git fetch --prune origin "+refs/tags/*:refs/tags/*"

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

NEW_VERSION=$MAJOR_VERSION.$MINOR_VERSION
echo "New version will be $NEW_VERSION"

cd $WORKSPACE

git tag -a $NEW_VERSION -m "New $MAJOR_MINOR release"

dch -v "$NEW_VERSION-1" "Built from git. $REV"
dch -D unstable -r ""

dpkg-buildpackage -A -rfakeroot -us -uc

ARTIFACTS_DIR=$WORKSPACE/$BUILD_NUMBER-ARTIFACTS
mkdir $ARTIFACTS_DIR
mv $WORKSPACE/../jibri_$NEW_VERSION* $ARTIFACTS_DIR


find $ARTIFACTS_DIR -name *.deb -exec gpg --batch --passphrase-file ~/.gnupg/passphrase -sba '{}' \;
ls -l $ARTIFACTS_DIR
mv $ARTIFACTS_DIR/jibri*{.deb,.changes} $REPO_DIR/mini-dinstall/incoming
mv $ARTIFACTS_DIR/jibri*.asc $REPO_DIR/unstable/signatures
ls -l $REPO_DIR/mini-dinstall/incoming

echo "Running jenkins-cli build update-repo-from-ci"
java -jar /var/lib/jenkins/bin/jenkins-cli.jar -s http://jenkins2.jitsi.net:8080 build update-repo-from-ci -f \
  -p FILES_TO_COPY="$REPO_DIR/mini-dinstall/incoming/*{*.changes,*.deb}" \
  --username "ci" \
  --password "$(cat /var/lib/jenkins/jenkins2-passwd)"

/var/lib/jenkins/bin/clean-oldies.sh unstable jibri

# call repo update script
mini-dinstall -b -c $MINID_CONF_FILE $REPO_DIR

# sync with download server
/var/lib/jenkins/bin/sync-repo.sh unstable

git push origin $NEW_VERSION
