package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

data class StreamingOptions(
        /**
         * The YouTube stream key to use for this stream
         */
        val youTubeStreamKey: String,
        val callParams: CallParams
)

/**
 * [StreamingJibriService] is the [JibriService] responsible for joining a
 * web call, capturing its audio and video, and streaming that audio and video
 * to a url
 */
class StreamingJibriService(val streamingOptions: StreamingOptions) : JibriService() {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val jibriSelenium = JibriSelenium(JibriSeleniumOptions(callParams = streamingOptions.callParams))
    private val capturer = FfmpegCapturer()
    private val sink: Sink
    private val STREAMING_MAX_BITRATE = 2976
    private val YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"
    /**
     * The [ScheduledExecutorService] we'll use to run the process monitor
     */
    private val executor = Executors.newSingleThreadScheduledExecutor()
    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null

    init {
        sink = StreamSink(
                url = "$YOUTUBE_URL/${streamingOptions.youTubeStreamKey}",
                streamingMaxBitrate = STREAMING_MAX_BITRATE,
                streamingBufSize = 2 * STREAMING_MAX_BITRATE)
    }

    /**
     * @see [JibriService.start]
     */
    override fun start() {
        jibriSelenium.joinCall(streamingOptions.callParams.callUrlInfo.callName)
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, sink)
        var numRestarts = 0
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            logger.error("Capturer process is no longer running, exited " +
                    "with code $exitCode.  Restarting.")
            if (numRestarts == 1) {
                logger.error("Giving up on restarting the capturer")
                publishStatus(Status.ERROR)
                stop()
            } else {
                numRestarts++
                capturer.start(capturerParams, sink)
            }
        }
        processMonitorTask = executor.scheduleAtFixedRate(
                period = Duration( 10, TimeUnit.SECONDS),
                action = processMonitor)
    }

    /**
     * @see [JibriService.stop]
     */
    override fun stop() {
        processMonitorTask?.cancel(true)
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
    }
}