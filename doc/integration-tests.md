# Integration tests

The unit tests mock the browser away.  These tests do the opposite: they run jibri's
[`CallPage`](../src/main/kotlin/org/jitsi/jibri/selenium/pageobjects/CallPage.kt) implementations against a
real [docker-jitsi-meet](https://github.com/jitsi/docker-jitsi-meet) deployment, with real participants in
the conference, so that the javascript those page objects execute is checked against the jitsi-meet it
actually has to work with.

Two things join every conference:

* **jibri**, driven by selenium through `AppCallPage` or `ExternalAPIPage` — jibri's own production code,
  which is what is under test.  Every scenario runs against both implementations, since jibri is expected
  to behave the same whichever one it uses.
* **participants**, driven by playwright — ordinary jitsi-meet clients with fake camera and microphone.
  These are only a test fixture; nothing about them is asserted on.

The tests live in `src/integration-test/kotlin` and are not part of the default build.

## Running them

Bring up a deployment, then run the tests against it:

```bash
./resources/integration-tests/deployment.sh up
mvn -Pintegration-tests test-compile failsafe:integration-test failsafe:verify
./resources/integration-tests/deployment.sh down
```

The images default to `unstable` because jibri master is developed against jitsi-meet master, and the
two do go out of sync: jibri's `ExternalAPIPage.setParticipantProperties` passes `useRawKeys`, which the
`stable` web image silently ignores, so on stable jibri's `session_id` and `mode` land in presence under
the wrong names. Set `DJM_VERSION=stable` to check what a released deployment does.

`deployment.sh` clones docker-jitsi-meet into `target/`, generates a configuration in
`target/jitsi-meet-cfg` and starts the containers.  It starts from an empty configuration every time, so
`up` throws away whatever the previous run left behind.  Useful environment variables:

| Variable | Default | |
| --- | --- | --- |
| `DJM_DIR` | `target/docker-jitsi-meet` | where docker-jitsi-meet is checked out, cloned if missing |
| `DJM_VERSION` | `unstable` | the image tag to run |
| `HTTPS_PORT` | `8443` | the port the deployment is served on |
| `JVB_ADVERTISE_IPS` | `127.0.0.1` | the address the browsers reach the jvb on |

The deployment serves a self-signed certificate on `https://localhost:8443`, which the tests tell their
browsers to accept.  It has to be https: the jitsi-meet configuration the web container generates hardcodes
`https`/`wss` for its bosh and websocket urls, so a plain-http deployment cannot be joined.

Options for the tests themselves:

```bash
# a deployment somewhere else
mvn -Pintegration-tests -Djibri.test.base-url=https://localhost:9443 ... 
# watch the browsers instead of running them headless
mvn -Pintegration-tests -Djibri.test.headless=false ...
```

### Media does not flow on docker desktop

The browsers run on the host and the jvb runs in a container with its media port published, which relies
on docker forwarding UDP to the host.  Docker Desktop (macOS, Windows) does not do this reliably, so ICE
never connects and the assertions that depend on media — `isIceConnected`, and the mute state of remote
participants — fail locally even though the deployment is healthy.  The scenarios that only need
signalling still run.  On Linux, where the host can reach the container directly, everything works, which
is what the `Integration tests` github workflow runs.

## What the deployment is configured for

`deployment.sh` turns on a few things a stock deployment leaves off, because jibri's behaviour depends on
them:

* **JWT authentication**, with empty tokens still allowed. Participants that need an identity join with a
  token; jibri and everyone else still join anonymously. Without an identity there is nothing for
  `getParticipants` to report and no way to mark a participant hidden from the recorder. `presence_identity`
  is added to the MUC modules to put that identity into presence, and jicofo authentication is left off so
  the first participant in a room is still its moderator — which is what the scenarios that kick jibri or
  turn on AV moderation depend on.
* **jicofo's REST interface**, so the readiness check can see when a bridge has joined the brewery.

## Adding tests

`CallPageIT` runs every scenario against both `CallPage` implementations.  Conferences settle
asynchronously, so assert through `await { }` rather than directly — a participant that just joined is not
yet visible to everyone, and mute state takes a moment to propagate.
