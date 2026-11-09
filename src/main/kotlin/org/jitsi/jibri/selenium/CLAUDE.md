# Selenium

## Call URL options

- `RECORDING_URL_OPTIONS` and `SIP_GW_URL_OPTIONS` (`JibriSelenium.kt`) are fixed lists. Add a per-request URL param in another place: `XmppApi.handleStartService` adds `config.analytics.rtcstatsEnabled` from the start IQ, and a recording profile adds its own params (`RecordingProfile.urlParams()`).

## Room check after the join

- `CallUrlInfo.authorizedRoom` is the room `EntityBareJid` from the start IQ. It is `@JsonIgnore`, so a request body cannot set it. When it is set, the call page compares it with the bare JID of the joined room, domain included. If they differ, Jibri leaves and fails the join before ffmpeg starts.
  - `AppCallPage` reads `APP.conference._room.room.roomjid`.
  - `ExternalAPIPage` reads the `jid` of the main room from `getRoomsInfo()`. Without breakout rooms it is the occupant JID, and with breakout rooms it is the room JID. The bare form is the room JID in both cases.
- The domain comparison depends on `config.iAmRecorder=true`, which Jibri sets in both URL option lists in `JibriSelenium.kt` and in `recorder.html`. With it, lib-jitsi-meet leaves out `visitors-version` in the conference request and ignores a `vnode` redirect (`modules/xmpp/moderator.js`), so Jicofo never moves Jibri to a visitor node. A visitor uses the MUC domain `<vnode>.meet.jitsi`, and the check would fail for it. Do not remove `config.iAmRecorder=true`.
