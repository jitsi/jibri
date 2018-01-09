package org.jitsi

enum class RecordingMode
{
    STREAM,
    FILE
}

/**
 * Contains the various configuration values needed when starting a recording
 *
 * TODO: what are the params here?
 * recordingMode = [STREAM, FILE]
 * streamid (optional, if doing STREAM recording mode)
 * client configuration data (xmpp login, boshdomain, etc.)
 * the call url
 * recording name
 */
class RecordingOptions
{
}