/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

data class StreamingOptions(
    /**
     * The YouTube stream key to use for this stream
     */
    val youTubeStreamKey: String,
    /**
     * The params needed to join the call
     */
    val callParams: CallParams
)

private const val YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"
private const val STREAMING_MAX_BITRATE = 2976

/**
 * [StreamingJibriService] is the [JibriService] responsible for joining a
 * web call, capturing its audio and video, and streaming that audio and video
 * to a url
 */
class StreamingJibriService(private val streamingOptions: StreamingOptions) : JibriService() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val capturer = FfmpegCapturer()
    private val sink: Sink
    /**
     * The [java.util.concurrent.ScheduledExecutorService] we'll use to run the process monitor
     */
    private val executor = Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("StreamingJibriService"))
    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null
    private val jibriSelenium = JibriSelenium(JibriSeleniumOptions(streamingOptions.callParams), executor)

    init {
        sink = StreamSink(
            url = "$YOUTUBE_URL/${streamingOptions.youTubeStreamKey}",
            streamingMaxBitrate = STREAMING_MAX_BITRATE,
            streamingBufSize = 2 * STREAMING_MAX_BITRATE
        )

        // Bubble up jibriSelenium's status
        jibriSelenium.addStatusHandler {
            publishStatus(it)
        }
    }

    override fun start(): Boolean {
        if (!jibriSelenium.joinCall(streamingOptions.callParams.callUrlInfo.callName)) {
            logger.error("Selenium failed to join the call")
            return false
        }
        logger.info("Selenium joined the call")
        if (!capturer.start(sink)) {
            logger.error("Capturer failed to start")
            return false
        }
        var numRestarts = 0
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            if (exitCode != null) {
                logger.error("Capturer process is no longer healthy.  It exited with code $exitCode")
            } else {
                logger.error("Capturer process is no longer healthy but it is still running, stopping it now")
            }
            if (numRestarts == FFMPEG_RESTART_ATTEMPTS) {
                logger.error("Giving up on restarting the capturer")
                publishStatus(JibriServiceStatus.ERROR)
            } else {
                numRestarts++
                capturer.stop()
                if (!capturer.start(sink)) {
                    logger.error("Capture failed to restart, giving up")
                    publishStatus(JibriServiceStatus.ERROR)
                }
            }
        }
        processMonitorTask = executor.scheduleAtFixedRate(processMonitor, 30, 10, TimeUnit.SECONDS)
        return true
    }

    override fun stop() {
        processMonitorTask?.cancel(false)
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Stopped capturer")
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
        logger.info("Quit selenium")
    }
}
