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

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium2
import org.jitsi.jibri.selenium.RECORDING_URL_OPTIONS
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStateMachine
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.service.toJibriServiceEvent
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.whenever
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

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
class StreamingJibriService(private val streamingParams: StreamingParams) : JibriService() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val capturer = FfmpegCapturer()
    private val sink: Sink
    private val stateMachine = JibriServiceStateMachine()
    //TODO: this will go away once we permeate the reactive stuff to the top
    private val allSubComponentsRunning = CompletableFuture<Boolean>()
    private val jibriSelenium = JibriSelenium2()

    init {
        sink = StreamSink(
            url = "$YOUTUBE_URL/${streamingParams.youTubeStreamKey}",
            streamingMaxBitrate = STREAMING_MAX_BITRATE,
            streamingBufSize = 2 * STREAMING_MAX_BITRATE
        )

        stateMachine.onStateTransition(this::onServiceStateChange)

        stateMachine.registerSubComponent(JibriSelenium2.COMPONENT_ID)
        jibriSelenium.addStatusHandler { state ->
            stateMachine.transition(state.toJibriServiceEvent(JibriSelenium2.COMPONENT_ID))
        }

        stateMachine.registerSubComponent(FfmpegCapturer.COMPONENT_ID)
        capturer.addStatusHandler { state ->
            stateMachine.transition(state.toJibriServiceEvent(FfmpegCapturer.COMPONENT_ID))
        }
    }

    private fun onServiceStateChange(@Suppress("UNUSED_PARAMETER") oldState: ComponentState, newState: ComponentState) {
        logger.info("Streaming service transition from state $oldState to $newState")
        when (newState) {
            is ComponentState.Running -> allSubComponentsRunning.complete(true)
            is ComponentState.Finished -> {
                allSubComponentsRunning.complete(false)
                publishStatus(JibriServiceStatus.FINISHED)
            }
            is ComponentState.Error -> {
                allSubComponentsRunning.complete(false)
                publishStatus(JibriServiceStatus.ERROR)
            }
        }
    }

    override fun start(): Boolean {
        jibriSelenium.joinCall(
                streamingParams.callParams.callUrlInfo.copy(urlParams = RECORDING_URL_OPTIONS),
                streamingParams.callLoginParams)

        whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
            logger.info("Selenium joined the call, starting capturer")
            capturer.start(sink)
        }

        jibriSelenium.addToPresence("session_id", streamingParams.sessionId)
        jibriSelenium.addToPresence("mode", JibriIq.RecordingMode.STREAM.toString())
        streamingParams.youTubeBroadcastId?.let {
            if (!jibriSelenium.addToPresence("live-stream-view-url", "http://youtu.be/$it")) {
                logger.error("Error adding live stream url to presence")
            }
        }
        jibriSelenium.sendPresence()

        println("Streaming service waiting for all sub components to start up")
        return allSubComponentsRunning.get()
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
