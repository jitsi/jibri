FROM alpine:latest
MAINTAINER Sam Whited <swhited@atlassian.com>

RUN ["apk", "update"]

RUN ["apk", "add", "chromium", "chromium-chromedriver"]
RUN ["apk", "add", "curl"]
RUN ["apk", "add", "ffmpeg"]
RUN ["apk", "add", "gcc", "musl-dev"]
RUN ["apk", "add", "imagemagick"]
RUN ["apk", "add", "mplayer"]
RUN ["apk", "add", "python3"]
RUN ["apk", "add", "xf86-video-dummy", "xorg-server"]

# Deps from testing
# TODO: Move these into the normal list when they're in the stable repos
RUN ["apk", "add", "--update-cache", "--repository", \
	"http://dl-3.alpinelinux.org/alpine/edge/testing/", "--allow-untrusted", \
	"icewm", "pulseaudio"]

RUN ["curl", "https://bootstrap.pypa.io/get-pip.py", "-o", "get-pip.py"]
RUN ["python3", "./get-pip.py"]

# Build and install dumb-init and Python deps (then remove build system)
ENV CC "musl-gcc"
COPY requirements.txt requirements.txt
RUN ["pip", "install", "-r", "requirements.txt"]
RUN ["pip", "install", "dumb-init"]
RUN ["apk", "del", "gcc", "musl-dev"]

WORKDIR /root

COPY asoundrc .asoundrc
COPY config.json config.json
COPY jibri-xmpp-client jibri-xmpp-client
COPY scripts scripts

RUN ["mkdir", "-p", "/var/run/jibri"]

CMD [ \
  "dumb-init", "python3", "./jibri-xmpp-client/app.py", \
	"-j $JIBRI_JID", \
	"-n $JIBRI_NICK", \
	"-p $JIBRI_PASS", \
	"-P $JIBRI_ROOMPASS", \
	"-r $JIBRI_ROOM", \
	"-t $JIBRI_TOKEN_SERVERS", \
	"-c", "config.json" \
]
