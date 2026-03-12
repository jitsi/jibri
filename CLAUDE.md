# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About Jibri

Jibri (JItsi BRoadcasting Infrastructure) provides services for recording or streaming Jitsi Meet conferences. It works by launching a Chrome instance in a virtual framebuffer, capturing and encoding output with ffmpeg. Only one recording at a time is supported per Jibri instance.

## Build and Test Commands

### Building
```bash
# Full build with tests and linting
mvn verify

# Package without running tests
mvn package -DskipTests

# Build JAR with dependencies
mvn clean package
# Output: target/jibri-8.0-SNAPSHOT-jar-with-dependencies.jar
```

### Testing
```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=SeleniumStateMachineTest

# Run a specific test method
mvn test -Dtest=SeleniumStateMachineTest#testMethodName
```

### Linting
```bash
# Run both Java checkstyle and Kotlin ktlint
mvn verify

# Run only ktlint
mvn ktlint:check

# Auto-format Kotlin code
mvn ktlint:format
```

## Architecture

### Core Components

1. **JibriManager** (`JibriManager.kt`) - Central orchestrator that manages Jibri services and tracks busy/idle state. Publishes status updates and enforces single-service-at-a-time constraint.

2. **Services** (in `service/impl/`) - Three main service types:
   - `FileRecordingJibriService` - Records conferences to local files
   - `StreamingJibriService` - Streams conferences to RTMP endpoints
   - `SipGatewayJibriService` - SIP gateway functionality

   All services extend `StatefulJibriService` and use a state machine pattern.

3. **APIs** - Three API layers for control:
   - `XmppApi` - XMPP-based control via MUC (JibriBrewery)
   - `HttpApi` - External HTTP API (port 2222) for start/stop recording
   - `InternalHttpApi` - Internal HTTP API (port 3333) for health checks and shutdown

4. **Selenium Layer** (`selenium/`) - Controls Chrome browser:
   - `JibriSelenium` - WebDriver wrapper for browser control
   - `SeleniumStateMachine` - Manages browser state transitions
   - Status checks in `status_checks/` monitor call health (media received, ICE connection, empty call detection)

5. **Capture Layer** (`capture/ffmpeg/`) - FFmpeg integration:
   - `FfmpegCapturer` - Manages ffmpeg process for audio/video capture
   - `FfmpegStatusStateMachine` - Tracks capture state
   - Platform-specific capture commands (Linux X11grab, macOS AVFoundation)

6. **Configuration** (`config/`) - Uses jitsi-metaconfig library:
   - `reference.conf` contains all default values
   - Override defaults in `/etc/jitsi/jibri/jibri.conf`
   - Config hot-reloading triggers graceful restart when idle

### Control Flow

1. Request arrives via XMPP or HTTP API
2. JibriManager checks if idle, creates appropriate service
3. Service starts Selenium (Chrome browser joins call)
4. Service starts FFmpeg (captures screen/audio)
5. Status checks monitor call health
6. On completion, service finalizes recording and returns to idle

### State Management

Extensive use of state machines via Tinder StateMachine library:
- `JibriServiceStateMachine` - Service lifecycle states
- `SeleniumStateMachine` - Browser states
- `FfmpegStatusStateMachine` - Capture states

All state transitions are logged and published to metrics.

## Configuration

Configuration is layered (priority order):
1. Command-line arguments
2. `jibri.conf` (HOCON format)
3. `reference.conf` (defaults in resources)

Key config sections:
- `jibri.api.xmpp.environments` - XMPP connection details
- `jibri.ffmpeg.*` - FFmpeg encoding parameters
- `jibri.chrome.flags` - Chrome launch flags
- `jibri.call-status-checks.*` - Timeouts for empty call detection

## Code Style

- Primary language: Kotlin (Java only in extreme cases)
- Follow Kotlin style guide (enforced by ktlint)
- Pre-commit hook available: `resources/add_git_pre_commit_script.sh`
- All Kotlin warnings are errors (`-Werror` compiler flag)

## Testing

- Test framework: Kotest (JUnit 5 compatible)
- Mocking: MockK for Kotlin
- Test helpers in `src/test/kotlin/org/jitsi/jibri/helpers/`
- Tests use in-memory filesystems for file operations

## Java Version

- Source/Target: Java 11 minimum
- Runtime: Java 21 supported (as of recent commits)
- Kotlin JVM target: 11

## Dependencies

Key external dependencies:
- Selenium WebDriver 3.12.0 - Browser automation
- Ktor 3.0 - HTTP server framework
- jicoco libraries - Jitsi common components (XMPP, config, metrics)
- Tinder StateMachine - State machine implementation
- FFmpeg - External binary for capture (not a Maven dependency)

## Important Notes

- Jibri requires exclusive access to display and audio devices
- Single-use mode: If `jibri.single-use-mode = true`, Jibri exits after one session
- Health checks exposed at internal HTTP API `/jibri/api/v1.0/health`
- Metrics available via StatsD and optionally Prometheus
- Webhook support for external status notifications
