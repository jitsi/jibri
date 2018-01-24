package org.jitsi.jibri.service

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.StreamSink

data class StreamingOptions(
        val streamUrl: String,
        val callUrlInfo: CallUrlInfo
)

class JibriStreamingService(streamingOptions: StreamingOptions) :
        JibriSeleniumFfmpegService(streamingOptions.callUrlInfo) {
    private val sink: Sink
    private val STREAMING_MAX_BITRATE = 2976

    init {
        sink = StreamSink(
                url = streamingOptions.streamUrl + "/" + "gx3c-aw44-hkda-5wrt",
                streamingMaxBitrate = STREAMING_MAX_BITRATE,
                streamingBufSize = 2 * STREAMING_MAX_BITRATE)
    }

    override fun getSink(): Sink = sink
}