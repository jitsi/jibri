package org.jitsi.jibri

import com.fasterxml.jackson.databind.util.JSONPObject
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.pjsua.PjSuaCapturer
import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.sink.Recording
import org.jitsi.jibri.sink.Stream
import java.io.File
import java.io.FileNotFoundException

/**
 * The main Jibri interface
 * Methods here are not thread safe, and should only be called from
 * the same thread
 */
class Jibri {
    private lateinit var jibriSelenium: JibriSelenium
    private lateinit var capturer: Capturer
    private lateinit var capturerMonitor: ProcessMonitor
    private lateinit var config: JibriConfig

    // TODO: force a (successful) call of this in ctor?
    fun loadConfig(configFilePath: String)
    {
        val mapper = jacksonObjectMapper()
        try {
            config = mapper.readValue<JibriConfig>(File(configFilePath))
        } catch (e: FileNotFoundException) {
            println("Unable to read config file ${configFilePath}")
            return
        }
        println("Read config: \n ${config}")
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
            Recording(recordingsDirectory = File(config.recordingDirectory), callName = jibriOptions.callName)
        }

        println("Starting selenium")
        //TODO: get url from somewhere
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = "https://meet.jit.si"))
        println("joining call")
        jibriSelenium.joinCall(jibriOptions.callName)
        // start ffmpeg or pjsua (how does pjsua work here?)
        capturer = if(jibriOptions.useSipGateway) PjSuaCapturer() else FfmpegCapturer()
        println("Starting capturer")
        capturer.start(CapturerParams(), sink)
        // monitor the capturer process to watch for issues
        capturerMonitor = ProcessMonitor(processToMonitor = capturer) { exitCode ->
            println("Capturer process is no longer running, exited with code: $exitCode")
            //NOTE: even though this will be executed on a separate thread
            // from the call to 'stopRecording', i don't think we have a
            // race condition here, since stopRecording will call capturer.stop
            // after stopping the monitor.
            capturer.start(CapturerParams(), sink)
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
     * as a json-formatted string
     */
    fun healthCheck(): String
    {
        val health = JibriHealth()
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(health)
    }
}