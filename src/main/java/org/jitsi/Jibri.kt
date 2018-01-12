package org.jitsi

import org.jitsi.capture.Capturer
import org.jitsi.capture.CapturerParams
import org.jitsi.capture.Monitor
import org.jitsi.capture.ffmpeg.FfmpegCapturer
import org.jitsi.capture.pjsua.PjSuaCapturer
import org.jitsi.selenium.JibriSelenium
import org.jitsi.selenium.JibriSeleniumOptions
import java.io.File

/**
 * The main Jibri interface
 */
class Jibri {
    private lateinit var jibriSelenium: JibriSelenium
    private lateinit var capturer: Capturer
    private lateinit var capturerMonitor: Monitor
    private val recordingsPath = "/tmp/recordings"
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
        println("Starting a recording, options: $recordingOptions")
        val sink: Sink = {
            if (recordingOptions.recordingMode == RecordingMode.STREAM)
            {
                // Use a stream sink
                Stream(recordingOptions.callName)
            }
            else
            {
                println("Creating recording sink")
                // Use a recording sink
                Recording(
                        recordingsPath = File(recordingsPath),
                        callName = recordingOptions.callName)
            }
        }()

        println("Starting selenium")
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = "https://meet.jit.si"))
        println("joining call")
        jibriSelenium.joinCall(recordingOptions.callName)
        // start ffmpeg or pjsua (how does pjsua work here?)
        capturer = if(recordingOptions.useSipGateway) PjSuaCapturer() else FfmpegCapturer()
        println("Starting capturer")
        sink.getPath()?.let {
            //NOTE(brian): bummer have to use the '!!' here even though i just
            // checked the result of getUri
            capturer.start(CapturerParams(sinkUri = sink.getPath()!!))
            // monitor the ffmpeg/pjsua status in some way to watch for issues
            capturerMonitor = Monitor(capturer) { exitCode ->
                println("Capturer process is no longer running, exited with code: $exitCode")
            }
        } ?: run {
            println("Error creating sink, can't start capturer")
        }
    }

    /**
     * Stop the current recording session
     */
    fun stopRecording()
    {
        // stop the recording and exit the call
        println("Stopping capturer")
        capturer.stop()
        println("Quitting selenium")
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