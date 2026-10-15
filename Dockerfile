FROM maven:3.6.3-openjdk-15 as vendor

WORKDIR /var/www/jibri

COPY . .

RUN mvn install -Dassembly.skipAssembly=false -DskipTests

FROM bitnamilegacy/kubectl:1.23.0 as kubectl

FROM jitsi/jibri:stable-10710

ARG CI_COMMIT_TAG
ENV SENTRY_RELEASE="${CI_COMMIT_TAG}"

COPY --from=kubectl /opt/bitnami/kubectl/bin/kubectl /usr/bin/kubectl
COPY --from=vendor /var/www/jibri/target/jibri-8.0-SNAPSHOT-jar-with-dependencies.jar /opt/jitsi/jibri/jibri.jar