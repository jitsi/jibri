# Building the Jibri debian package
Building the debian package has been tested on Ubuntu Xenial.

### Steps
* Install the prerequisites:
  * `sudo apt install git maven openjdk-8-jdk`
  * (`openjdk-8-jdk` specifically is not necessarily required, any java 8 jdk will probably work)
* Clone the repo:
  * `git clone https://github.com/jitsi/jibri.git`
* Create the jar:
  * `cd jibri`
  * `mvn package`
* Call the `create_debian_package_script` and pass it the location of the jar:
  * `` export WORKSPACE=`pwd` ``
  * `resources/jenkins/release.sh Minor`
