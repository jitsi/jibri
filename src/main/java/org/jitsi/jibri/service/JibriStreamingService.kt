package org.jitsi.jibri.service

import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.StreamSink

data class StreamingOptions(
        val streamUrl: String,
        val baseUrl: String,
        val callName: String
)

class JibriStreamingService(val streamingOptions: StreamingOptions) : JibriService {
    private val sink: Sink
    private val STREAMING_MAX_BITRATE = 2976
    private val jibriSelenium: JibriSelenium
    private val capturer: Capturer

    init {
        sink = StreamSink(
                url = streamingOptions.streamUrl,
                streamingMaxBitrate = STREAMING_MAX_BITRATE,
                streamingBufSize = 2 * STREAMING_MAX_BITRATE)
        //TODO: this overlaps with the file recorder, should try and re-use
        // the selenium/ffmpeg logic
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = streamingOptions.baseUrl))
        capturer = FfmpegCapturer()
    }

    @Synchronized
    override fun start() {

    }

    override fun stop() {

    }
}