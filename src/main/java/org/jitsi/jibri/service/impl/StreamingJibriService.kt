package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.StreamSink
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
class StreamingJibriService(streamingOptions: StreamingOptions) :
        AbstractFfmpegSeleniumService(streamingOptions.callParams) {
    override val logger = Logger.getLogger(this::class.qualifiedName)

    private val sink: Sink = StreamSink(
        url = "$YOUTUBE_URL/${streamingOptions.youTubeStreamKey}",
        streamingMaxBitrate = STREAMING_MAX_BITRATE,
        streamingBufSize = 2 * STREAMING_MAX_BITRATE
    )

    override fun getSink(): Sink {
        return sink
    }
}
