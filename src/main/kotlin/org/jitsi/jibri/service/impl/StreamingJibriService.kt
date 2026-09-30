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

import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.metrics.JibriMetrics
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.selenium.RECORDING_URL_OPTIONS
import org.jitsi.jibri.service.ErrorSettingPresenceFields
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.RecordingProfile
import org.jitsi.jibri.service.RequestedRecordingParams
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.Resolution
import org.jitsi.jibri.util.whenever
import org.jitsi.xmpp.extensions.jibri.JibriIq

const val YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"

/** Whether [this] looks like a full RTMP URL, as opposed to a bare stream key. */
internal fun String.isRtmpUrl(): Boolean =
    startsWith("rtmp://", ignoreCase = true) || startsWith("rtmps://", ignoreCase = true)

/**
 * Parameters needed for starting a [StreamingJibriService]
 */
data class StreamingParams(
    /**
     * Which call we'll join
     */
    val callParams: CallParams,
    /**
     * The ID of this session
     */
    val sessionId: String,
    /**
     * The login information needed to appear invisible in
     * the call
     */
    val callLoginParams: XmppCredentials,
    /**
     * The RTMP URL we'll stream to
     */
    val rtmpUrl: String,
    /**
     * The URL at which the stream can be viewed
     */
    val viewingUrl: String? = null,
    /**
     * What the stream must look like, as asked for in the request, or null if the request asks for the default
     * stream.
     */
    val recordingParams: RequestedRecordingParams? = null,
    /**
     * What we do for [recordingParams], after we accepted them. [JibriManager] sets this.
     */
    val recordingProfile: RecordingProfile? = null,
    /**
     * The resolution for ffmpeg to capture, or null to use the resolution in the ffmpeg config. [JibriManager] sets
     * this.
     */
    val captureResolution: Resolution? = null
)

/**
 * [StreamingJibriService] is the [JibriService] responsible for joining a
 * web call, capturing its audio and video, and streaming that audio and video
 * to a url
 */
class StreamingJibriService(
    private val streamingParams: StreamingParams,
    jibriSelenium: JibriSelenium? = null,
    capturer: FfmpegCapturer? = null,
    private val jibriMetrics: JibriMetrics = JibriMetrics()
) : StatefulJibriService("Streaming") {
    init {
        logger.addContext("session_id", streamingParams.sessionId)
    }
    private val capturer = capturer ?: FfmpegCapturer(logger, resolution = streamingParams.captureResolution)
    private val sink: Sink
    private val jibriSelenium = jibriSelenium ?: JibriSelenium(
        logger,
        JibriSeleniumOptions(
            extraCallStatusChecks = listOfNotNull(
                streamingParams.recordingProfile?.createTileSizeCheck(logger) { jibriMetrics.tileSizeMismatch() }
            )
        )
    )

    init {
        sink = StreamSink(url = streamingParams.rtmpUrl)

        registerSubComponent(JibriSelenium.COMPONENT_ID, this.jibriSelenium)
        registerSubComponent(FfmpegCapturer.COMPONENT_ID, this.capturer)
    }

    override fun start() {
        val recordingProfile = streamingParams.recordingProfile
        jibriSelenium.joinCall(
            streamingParams.callParams.callUrlInfo.copy(
                urlParams = RECORDING_URL_OPTIONS + streamingParams.callParams.extraUrlParams +
                    (recordingProfile?.urlParams() ?: emptyList())
            ),
            streamingParams.callLoginParams
        )

        whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
            logger.info("Selenium joined the call, starting capturer")
            try {
                val properties = mutableMapOf(
                    "session_id" to streamingParams.sessionId,
                    "mode" to JibriIq.RecordingMode.STREAM.toString()
                )
                streamingParams.viewingUrl?.let { viewingUrl ->
                    properties["live-stream-view-url"] = viewingUrl
                }
                if (!jibriSelenium.setParticipantProperties(properties)) {
                    logger.error("Error setting presence properties")
                }
                if (recordingProfile?.useTileView == true && !jibriSelenium.setTileView(true)) {
                    logger.error("Failed to put the client in tile view")
                }
                capturer.start(sink)
            } catch (t: Throwable) {
                logger.error("Error while setting fields in presence", t)
                publishStatus(ComponentState.Error(ErrorSettingPresenceFields))
            }
        }
    }

    override fun stop() {
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Stopped capturer")
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
        logger.info("Quit selenium")
    }
}
