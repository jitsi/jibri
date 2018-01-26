package org.jitsi.jibri.service

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.StreamSink
import org.jitsi.jibri.util.Duration
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.error
import org.jitsi.jibri.util.scheduleAtFixedRate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

data class StreamingOptions(
        val streamUrl: String,
        val callUrlInfo: CallUrlInfo
)

class JibriStreamingService(val streamingOptions: StreamingOptions) : JibriService {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = streamingOptions.callUrlInfo.baseUrl))
    private val capturer = FfmpegCapturer()
    private val sink: Sink
    private val STREAMING_MAX_BITRATE = 2976
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var processMonitorTask: ScheduledFuture<*>? = null

    init {
        sink = StreamSink(
                url = streamingOptions.streamUrl + "/" + "gx3c-aw44-hkda-5wrt",
                streamingMaxBitrate = STREAMING_MAX_BITRATE,
                streamingBufSize = 2 * STREAMING_MAX_BITRATE)
    }

    override fun start() {
        jibriSelenium.joinCall(streamingOptions.callUrlInfo.callName)
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, sink)
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            logger.error("Capturer process is no longer running, exited " +
                    "with code $exitCode.  Restarting.")
            capturer.start(capturerParams, sink)
        }
        processMonitorTask = executor.scheduleAtFixedRate(
                period = Duration( 10, TimeUnit.SECONDS),
                action = processMonitor)
    }

    override fun stop() {
        processMonitorTask?.cancel(true)
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()

    }
}