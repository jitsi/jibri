package org.jitsi.jibri.service

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.error
import java.util.logging.Logger

// TODO: ugh, this name...
/**
 * This base class contains the common logic for services which use
 * selenium to join a call and ffmpeg to capture media from the call.
 * It does not handle the sink logic, which must be provided by the implementor
 */
abstract class JibriSeleniumFfmpegService(val callUrlInfo: CallUrlInfo) : JibriService {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = callUrlInfo.baseUrl))
    private val capturer = FfmpegCapturer()
    private var capturerMonitor: ProcessMonitor? = null

    @Synchronized
    override fun start() {
        jibriSelenium.joinCall(callUrlInfo.callName)
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, getSink())
        capturerMonitor = ProcessMonitor(processToMonitor = capturer) { exitCode ->
            logger.error("Capturer process is no longer running, exited " +
                    "with code $exitCode")
            //TODO: will this append to the existing file? or will it overwrite?
            // does ffmpeg support appending? or do we need to use a different file?
            capturer.start(capturerParams, getSink())
            capturerMonitor!!.startMonitoring()
        }
        capturerMonitor!!.startMonitoring()
    }

    override fun stop() {
        capturerMonitor?.stopMonitoring()
        capturerMonitor = null
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
        logger.info("Finalizing the recording")
        finalize()
    }

    protected abstract fun getSink(): Sink

    protected open fun finalize() {
        // No-op by default, subclasses can override
    }
}