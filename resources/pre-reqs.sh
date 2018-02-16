#!/bin/bash

die () {
    echo >&2 "$@"
    exit 1
}

# Install chrome and chromedriver
echo "Installing chrome"
# Adapted from https://gist.github.com/ziadoz/3e8ab7e944d02fe872c3454d17af31a5
curl -sS -o - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list
apt-get -y update
apt-get -y install google-chrome-stable
rc=$?; if [[ $rc != 0 ]]; then die "Error installing Chrome"; fi
echo "Done installing chrome"

echo "Installing chromedriver"
CHROME_DRIVER_VERSION=`curl -sS chromedriver.storage.googleapis.com/LATEST_RELEASE`
wget -N http://chromedriver.storage.googleapis.com/$CHROME_DRIVER_VERSION/chromedriver_linux64.zip -P ~/
unzip ~/chromedriver_linux64.zip -d ~/
rm ~/chromedriver_linux64.zip
sudo mv -f ~/chromedriver /usr/local/bin/chromedriver
sudo chown root:root /usr/local/bin/chromedriver
sudo chmod 0755 /usr/local/bin/chromedriver
echo "Done installing chromedriver"
