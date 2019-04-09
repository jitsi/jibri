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

import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.RECORDING_URL_OPTIONS
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.whenever

private const val YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"
private const val STREAMING_MAX_BITRATE = 2976

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
     * The YouTube stream key to use for this stream
     */
    val youTubeStreamKey: String,
    /**
     * The YouTube broadcast ID for this stream, if we have it
     */
    val youTubeBroadcastId: String? = null
)

/**
 * [StreamingJibriService] is the [JibriService] responsible for joining a
 * web call, capturing its audio and video, and streaming that audio and video
 * to a url
 */
class StreamingJibriService(
    private val streamingParams: StreamingParams
) : StatefulJibriService("Streaming") {
    private val capturer = FfmpegCapturer()
    private val sink: Sink
    private val jibriSelenium = JibriSelenium()

    init {
        sink = StreamSink(
            url = "$YOUTUBE_URL/${streamingParams.youTubeStreamKey}",
            streamingMaxBitrate = STREAMING_MAX_BITRATE,
            streamingBufSize = 2 * STREAMING_MAX_BITRATE
        )

        registerSubComponent(JibriSelenium.COMPONENT_ID, jibriSelenium)
        registerSubComponent(FfmpegCapturer.COMPONENT_ID, capturer)
    }

    override fun start() {
        jibriSelenium.joinCall(
                streamingParams.callParams.callUrlInfo.copy(urlParams = RECORDING_URL_OPTIONS),
                streamingParams.callLoginParams)

        whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
            logger.info("Selenium joined the call, starting capturer")
            try {
                jibriSelenium.addToPresence("session_id", streamingParams.sessionId)
                jibriSelenium.addToPresence("mode", JibriIq.RecordingMode.STREAM.toString())
                streamingParams.youTubeBroadcastId?.let {
                    if (!jibriSelenium.addToPresence("live-stream-view-url", "http://youtu.be/$it")) {
                        logger.error("Error adding live stream url to presence")
                    }
                }
                jibriSelenium.sendPresence()
                capturer.start(sink)
            } catch (t: Throwable) {
                logger.error("Error while setting fields in presence", t)
                publishStatus(ComponentState.Error(ErrorScope.SESSION, "Unable to set presence values"))
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
