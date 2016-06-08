FROM alpine:latest
MAINTAINER Sam Whited <swhited@atlassian.com>

RUN ["apk", "update"]

RUN ["apk", "add", "chromium"]
RUN ["apk", "add", "chromium-chromedriver"]
RUN ["apk", "add", "curl"]
RUN ["apk", "add", "ffmpeg"]
RUN ["apk", "add", "imagemagick"]
RUN ["apk", "add", "mplayer"]
RUN ["apk", "add", "python3"]
RUN ["apk", "add", "xf86-video-dummy"]
RUN ["apk", "add", "xorg-server"]

# Deps from testing
# TODO: Move these into the normal list when they're in the stable repos
RUN ["apk", "add", "--update-cache", "--repository", \
	"http://dl-3.alpinelinux.org/alpine/edge/testing/", "--allow-untrusted", \
	"icewm", "pulseaudio"]

WORKDIR /root

COPY asoundrc .asoundrc
COPY config.json config.json
COPY jibri-xmpp-client jibri-xmpp-client
COPY requirements.txt requirements.txt
COPY scripts scripts

RUN ["curl", "https://bootstrap.pypa.io/get-pip.py", "-o", "get-pip.py"]
RUN ["python3", "./get-pip.py"]
RUN ["pip", "install", "-r", "requirements.txt"]

# Build and install dumb-init (then remove build system)
RUN ["apk", "add", "gcc", "musl-dev"]
ENV CC "musl-gcc"
RUN ["pip", "install", "dumb-init"]
RUN ["apk", "del", "gcc", "musl-dev"]

CMD ["dumb-init", "python3", "./jibri-xmpp-client/app.py", "-j $JIBRI_JID", \
	"-p $JIBRI_PASS", "-r $JIBRI_ROOM", "-n $JIBRI_NICK", "-P $JIBRI_ROOMPASS", \
	"-t $JIBRI_TOKEN_SERVERS", "-c config.json"]
