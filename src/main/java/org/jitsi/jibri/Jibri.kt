package org.jitsi.jibri

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
import org.jitsi.jibri.util.error
import java.io.File
import java.io.FileNotFoundException
import java.util.logging.Logger

enum class StartRecordingResult {
    OK,
    ALREADY_RECORDING,
    ERROR
}

/**
 * The main Jibri interface
 */
class Jibri {
    private lateinit var jibriSelenium: JibriSelenium
    private lateinit var capturer: Capturer
    private lateinit var capturerMonitor: ProcessMonitor
    private lateinit var config: JibriConfig
    private var recordingActive = false
    private val logger = Logger.getLogger(this::class.simpleName)

    // TODO: force a (successful) call of this in ctor?
    // and only load it at start? (require restart to load new config?
    // must allow a signal to shutting down when possible (now or when
    // current recording is done)
    fun loadConfig(configFilePath: String)
    {
        val mapper = jacksonObjectMapper()
        try {
            config = mapper.readValue<JibriConfig>(File(configFilePath))
        } catch (e: FileNotFoundException) {
            logger.error("Unable to read config file ${configFilePath}")
            return
        }
        logger.info("Read config:\n + $config")
    }

    /**
     * Start a recording session
     */
    @Synchronized
    fun startRecording(jibriOptions: JibriOptions): StartRecordingResult
    {
        if (recordingActive) {
            return StartRecordingResult.ALREADY_RECORDING
        }
        recordingActive = true
        logger.info("Starting a recording, options: $jibriOptions")
        val sink = if (jibriOptions.recordingSinkType == RecordingSinkType.STREAM) {
            Stream(jibriOptions.streamUrl!!, 2976, 2976 * 2)
        } else {
            Recording(recordingsDirectory = File(config.recordingDirectory), callName = jibriOptions.callName)
        }

        logger.info("Starting selenium")
        //TODO: how best to deal with jibriSelenium (and capturer and
        // captureMonitor)?  they are
        // member values and cannot be initialized right away so:
        // 1) could use lateinit, but other code might try to access it before it
        //  is initialized (like if stop was called before start)
        // 2) could mark every call with ?., but we do need to cover the 'else'
        //  case there (if the instance is null for some reason, we should just return)
        // 3) could introduce a new variable that we know is non-null
        //  and use that (introducing another variable is a bummer i think)
        // in every case, if we handle failure for one step we need to make sure
        //  to do any cleanup necessary
        //TODO: return error if anything here fails to start up
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = jibriOptions.baseUrl))
        logger.info("Joining call ${jibriOptions.callName} on base url ${jibriOptions.baseUrl}")
        jibriSelenium.joinCall(jibriOptions.callName)
        // start ffmpeg or pjsua (how does pjsua work here?)
        capturer = if(jibriOptions.useSipGateway) PjSuaCapturer() else FfmpegCapturer()
        logger.info("Starting capturer")
        capturer.start(CapturerParams(), sink)
        // monitor the capturer process to watch for issues
        capturerMonitor = ProcessMonitor(processToMonitor = capturer) { exitCode ->
            logger.error("Capturer process is no longer running, exited with code: $exitCode")
            //NOTE: even though this will be executed on a separate thread
            // from the call to 'stopRecording', i don't think we have a
            // race condition here, since stopRecording will call capturer.stop
            // after stopping the monitor.
            capturer.start(CapturerParams(), sink)
        }
        capturerMonitor.startMonitoring()
        return StartRecordingResult.OK
    }

    /**
     * Stop the current recording session
     */
    @Synchronized
    fun stopRecording()
    {
        if (!recordingActive) {
            logger.info("Stop recording request recieved but we weren't recording")
            return
        }
        // stop monitoring
        if (this::capturerMonitor.isInitialized) {
            capturerMonitor.stopMonitoring()
        }
        // stop the recording and exit the call
        if (this::capturer.isInitialized) {
            logger.info("Stopping capturer")
            capturer.stop()
        }
        if (this::jibriSelenium.isInitialized) {
            logger.info("Quitting selenium")
            jibriSelenium.leaveCallAndQuitBrowser()
        }
        // finalize the recording
    }

    /**
     * Return some status indicating the health of this jibri
     * as a json-formatted string
     */
    @Synchronized
    fun healthCheck(): String
    {
        val health = JibriHealth()
        health.recording = recordingActive
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(health)
    }
}