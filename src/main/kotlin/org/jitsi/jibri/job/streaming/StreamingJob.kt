/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */

package org.jitsi.jibri.job.streaming

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jitsi.jibri.RtmpUrlNotAllowed
import org.jitsi.jibri.capture.ffmpeg.FfmpegHelpers
import org.jitsi.jibri.capture.ffmpeg.getFfmpegCommand
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.job.IntermediateJobState
import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.Running
import org.jitsi.jibri.job.StartingUp
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.ObserverUrlOptions
import org.jitsi.jibri.selenium.Selenium
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.util.logOnException
import org.jitsi.jibri.util.withFfmpeg
import org.jitsi.jibri.util.withSelenium
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.xmpp.extensions.jibri.JibriIq
import java.util.regex.Pattern

const val YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"
private const val STREAMING_MAX_BITRATE = 2976

/**
 * Parameters needed for starting a [StreamingJob]
 */
data class StreamingParams(
    /**
     * The RTMP URL we'll stream to
     */
    val rtmpUrl: String,
    /**
     * The URL at which the stream can be viewed
     */
    val viewingUrl: String? = null
)

class StreamingJob(
    parentLogger: Logger,
    private val sessionId: String,
    private val callParams: CallParams,
    private val streamingParams: StreamingParams
) : JibriJob {
    private val logger = createChildLogger(parentLogger, mapOf("session-id" to sessionId))
    private val _state = MutableStateFlow<IntermediateJobState>(StartingUp)
    override val state: StateFlow<IntermediateJobState> = _state.asStateFlow()
    override val name: String = "Streaming job $sessionId"

    private val rtmpAllowList: List<Pattern> by config {
        "jibri.streaming.rtmp-allow-list".from(Config.configSource)
            .convertFrom<List<String>> { it.map(Pattern::compile) }
    }

    private val sink = StreamSink(
        url = streamingParams.rtmpUrl,
        streamingMaxBitrate = STREAMING_MAX_BITRATE,
        streamingBufSize = 2 * STREAMING_MAX_BITRATE
    )

    init {
        if (rtmpAllowList.none { it.matcher(streamingParams.rtmpUrl).matches() }) {
            logger.error("RTMP url ${streamingParams.rtmpUrl} is not allowed")
            throw RtmpUrlNotAllowed(streamingParams.rtmpUrl)
        }
    }

    override suspend fun run() {
        logger.info("Running")
        withSelenium(Selenium(logger)) { selenium ->
            selenium.joinCall(
                callParams.callUrlInfo.copy(urlParams = ObserverUrlOptions),
                callParams.callLogin
            )

            selenium.addToPresence("session_id", sessionId)
            selenium.addToPresence("mode", JibriIq.RecordingMode.STREAM.toString())
            streamingParams.viewingUrl?.let { viewingUrl ->
                if (!selenium.addToPresence("live-stream-view-url", viewingUrl)) {
                    logger.error("Error adding live stream url to presence")
                }
            }
            selenium.sendPresence()

            val ffmpegCommand = getFfmpegCommand(sink)
            logger.info("Starting ffmpeg via ${ffmpegCommand.joinToString(separator = " ")} ($ffmpegCommand)")
            withFfmpeg(ffmpegCommand) { ffmpeg ->
                coroutineScope {
                    launch {
                        FfmpegHelpers.onEncodingStart(ffmpeg) {
                            logger.info("Ffmpeg started successfully")
                            _state.value = Running
                        }
                    }
                    launch {
                        logOnException({ logger.error("Ffmpeg: ${it.message}") }) {
                            FfmpegHelpers.watchForProcessError(ffmpeg)
                        }
                    }
                    launch {
                        logOnException({ logger.error("Selenium: ${it.message}") }) {
                            selenium.monitorCall()
                        }
                    }
                }
            }
        }
    }
}

fun String.isRtmpUrl(): Boolean =
    startsWith("rtmp://", ignoreCase = true) || startsWith("rtmps://", ignoreCase = true)
fun String.isViewingUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
