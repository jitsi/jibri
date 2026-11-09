# Screen and recording params

## X screen

- The X server is long-lived, and all sessions on one host share it. `jibri-xorg.service` (`resources/debian-package/etc/systemd/system/jibri-xorg.service`) starts it once, not for each session. It runs with `+extension RANDR`, so `xrandr` can change the screen mode at runtime.
- The screen modes come from `resources/debian-package/etc/jitsi/jibri/xorg-video-dummy.conf` (installed at `/etc/jitsi/jibri/xorg-video-dummy.conf`). The X server fixes the virtual screen size when it starts. xrandr can switch only to a mode that has a `Modeline` and is in the `Modes` line of the Screen section. A new mode needs an X server restart.
- Each session starts a new Chrome, because each service creates its own `JibriSelenium`. Chrome runs with `--start-maximized --kiosk` (`jibri.chrome.flags`) and reads the screen size when it starts. Set the screen resolution before you create the service, as `JibriManager` does.

## Recording params

- A start IQ for a file recording or a stream can carry `<recording-params tile-resolution tile-count max-full-resolution-participants/>`. `JibriManager` checks it before it marks the instance busy. It refuses a request that it cannot serve as a bad request.
- Example: `<recording-params tile-resolution='1280x720' tile-count='2' max-full-resolution-participants='3'/>` (`RecordingParamsPacketExt` in jitsi-xmpp-extensions). `XmppApi` reads it with `JibriIq.recordingParams()` and does not check the values. Jicofo builds the element from the room metadata field `recordingParams`.
- `RecordingProfiles.resolve()` (`RecordingParams.kt`) turns the raw strings into a `RecordingProfile`, or throws `BadRequestException`. `StartRequestValidator` calls it. `tile-resolution` and `tile-count` must come together. The canvas for a tile layout comes from `jibri.screen.tile-canvases` (default: 2 tiles of 1280x720 on a `2582x748` canvas). Jibri refuses a combination that is not in this list.
- `jibri.screen.xrandr-enabled` (off by default) lets Jibri set the X screen with xrandr before every session. When it is off, Jibri never touches the screen, and it refuses a tile layout as a bad request.
- `Screen` (`Screen.kt`) controls the X screen through `Xrandr` (`util/Xrandr.kt`). When `jibri.screen.xrandr-enabled` is on, `JibriManager.prepareScreen()` sets the resolution before every session (file, stream and SIP). It sets the canvas of the tile layout, or `jibri.screen.default-resolution` (default `${jibri.ffmpeg.resolution}`). `stopService()` restores the default. If xrandr fails, Jibri publishes `UNHEALTHY` and does not start the session.
- `FfmpegCapturer` takes an optional `resolution`. It replaces the value after `-s` (Linux) or `-video_size` (macOS) in the configured ffmpeg command.
- A tile profile adds `config.disableTileEnlargement=true` to the call URL, and turns on tile view after the join (`setTileView`; `AppCallPage` dispatches `SET_TILE_VIEW`). A requested `max-full-resolution-participants` adds `config.maxFullResolutionParticipants=N`, also without a tile layout. `TileSizeStatusCheck` waits until the requested number of tiles shows, compares the tile size once, and counts a mismatch in the `tile_size_mismatches` metric. It never fails the session. `ExternalAPIPage` cannot read the tile layout, so the check does nothing there.
