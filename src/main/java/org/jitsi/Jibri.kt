package org.jitsi

import org.jitsi.capture.Capturer
import org.jitsi.capture.CapturerParams
import org.jitsi.capture.Monitor
import org.jitsi.capture.ffmpeg.FfmpegCapturer
import org.jitsi.capture.pjsua.PjSuaCapturer
import org.jitsi.selenium.JibriSelenium
import org.jitsi.selenium.JibriSeleniumOptions

/**
 * The main Jibri interface
 */
class Jibri {
    lateinit var jibriSelenium: JibriSelenium
    lateinit var capturer: Capturer
    lateinit var capturerMonitor: Monitor
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
        if (recordingOptions.recordingMode == RecordingMode.STREAM)
        {
            // get stream url
        }
        else
        {
            // generate a filename
            //TODO: the filename and stream url seemed to be used as the same
            // argument down the line, is this how ffmpeg's args work? check
            // with aaron
        }
        // create the path to store the recording (if to a file)
        // launch selenium
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = "https://meet.jit.si"))
        jibriSelenium.joinCall(recordingOptions.callName)
        // join the call (with jibri credentials -> look to add url params for
        // these so we don't have to do the 'double join')
        // start ffmpeg or pjsua (how does pjsua work here?)
        capturer = if(recordingOptions.useSipGateway) PjSuaCapturer() else FfmpegCapturer()
        capturer.start(CapturerParams(sinkUri = "dummySinkUri"))
        // monitor the ffmpeg/pjsua status in some way to watch for issues
        capturerMonitor = Monitor(capturer) { exitCode ->
            println("Capturer process is no longer running, exited with code: $exitCode")
        }
    }

    /**
     * Stop the current recording session
     */
    fun stopRecording()
    {
        // stop the recording and exit the call
        capturer.stop()
        jibriSelenium.leaveCallAndQuitBrowser()
        // finalize the recording
    }

    /**
     * Return some status indicating the health of this jibri
     */
    fun healthCheck()
    {

    }
}