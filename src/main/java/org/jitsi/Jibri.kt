package org.jitsi

/**
 * The main Jibri interface
 */
class Jibri {
    /**
     * TODO: stuff we'll read from here:
     * existing:
     * "jidserver_prefix":"auth.",
     * "mucserver_prefix":"conference.",
     * "boshdomain_prefix":"recorder.",
     * "password":"jibri",
     * "recording_directory":"./recordings",
     * "jid_username":"jibri",
     * "roomname":"TheBrewery",
     * "xmpp_domain":"xmpp.domain.name",
     * "selenium_xmpp_prefix":"recorder.",
     * "selenium_xmpp_username":"recorder",
     * "selenium_xmpp_password":"recorderpass",
     * "servers":["10.0.0.10"],
     * "environments":{...}
     */
    fun loadConfig(configFilePath: String)
    {
    }

    /**
     * Start a recording session
     */
    fun startRecording(recordingOptions: RecordingOptions)
    {
        // create the path to store the recording (if to a file)
        // launch selenium
        // join the call (with jibri credentials -> look to add url params for
        // these so we don't have to do the 'double join')
        // start ffmpeg or pjsua (how does pjsua work here?)
        // monitor the ffmpeg/pjsua status in some way to watch for issues
    }

    /**
     * Stop the current recording session
     */
    fun stopRecording()
    {
        // stop the recording
        // finalize the recording (via the script)
    }

    /**
     * Return some status indicating the health of this jibri
     */
    fun healthCheck()
    {

    }
}