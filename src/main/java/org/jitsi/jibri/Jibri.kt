package org.jitsi.jibri

import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.pjsua.PjSuaCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.sink.Recording
import org.jitsi.jibri.sink.Stream
import java.io.File

/**
 * The main Jibri interface
 */
class Jibri {
    private lateinit var jibriSelenium: JibriSelenium
    private lateinit var capturer: Capturer
    private lateinit var capturerMonitor: ProcessMonitor
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
    fun startRecording(jibriOptions: JibriOptions)
    {
        println("Starting a recording, options: $jibriOptions")
        val sink = if (jibriOptions.recordingMode == RecordingMode.STREAM) {
            Stream(jibriOptions.streamUrl!!, 2976, 2976 * 2)
        } else {
            Recording(recordingsPath = File(recordingsPath), callName = jibriOptions.callName)
        }

        println("Starting selenium")
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = "https://meet.jit.si"))
        println("joining call")
        jibriSelenium.joinCall(jibriOptions.callName)
        // start ffmpeg or pjsua (how does pjsua work here?)
        capturer = if(jibriOptions.useSipGateway) PjSuaCapturer() else FfmpegCapturer()
        println("Starting capturer")
        capturer.start(CapturerParams(), sink)
        // monitor the ffmpeg/pjsua status in some way to watch for issues
        capturerMonitor = ProcessMonitor(capturer) { exitCode ->
            println("Capturer process is no longer running, exited with code: $exitCode")
            //TODO:restart it.
        }
        capturerMonitor.startMonitoring()
    }

    /**
     * Stop the current recording session
     */
    fun stopRecording()
    {
        // stop monitoring
        capturerMonitor.stopMonitoring()
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