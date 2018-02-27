package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.ffmpeg.executor.impl.FFMPEG_RESTART_ATTEMPTS
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
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
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val capturer = FfmpegCapturer()
    private val sink: Sink
    private val STREAMING_MAX_BITRATE = 2976
    private val YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"
    /**
     * The [ScheduledExecutorService] we'll use to run the process monitor
     */
    private val executor = Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("StreamingJibriService"))
    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null
    private val jibriSelenium = JibriSelenium(JibriSeleniumOptions(callParams = streamingOptions.callParams), executor)

    init {
        sink = StreamSink(
                url = "$YOUTUBE_URL/${streamingOptions.youTubeStreamKey}",
                streamingMaxBitrate = STREAMING_MAX_BITRATE,
                streamingBufSize = 2 * STREAMING_MAX_BITRATE)

        jibriSelenium.addStatusHandler {
            publishStatus(it)
        }
    }

    /**
     * @see [JibriService.start]
     */
    override fun start() {
        jibriSelenium.joinCall(streamingOptions.callParams.callUrlInfo.callName)
        logger.info("Selenium joined the call")
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, sink)
        var numRestarts = 0
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            if (exitCode != null) {
                logger.error("Capturer process is no longer healthy.  It exited with code $exitCode")
            } else {
                logger.error("Capturer process is no longer healthy, but it is still running, stopping it now")
                capturer.stop()
            }
            if (numRestarts == FFMPEG_RESTART_ATTEMPTS) {
                logger.error("Giving up on restarting the capturer")
                publishStatus(JibriServiceStatus.ERROR)
                stop()
            } else {
                numRestarts++
                capturer.start(capturerParams, sink)
            }
        }
        processMonitorTask = executor.scheduleAtFixedRate(
            delay = 30,
            period = 10,
            unit = TimeUnit.SECONDS,
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
