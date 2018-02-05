package org.jitsi.jibri.sink.impl

import org.jitsi.jibri.sink.Sink

/**
 * [StreamSink] represents a sink which will write to a network stream
 */
class StreamSink(val url: String, val streamingMaxBitrate: Int, val streamingBufSize: Int) : Sink
{
    /**
     * See [Sink.getPath]
     */
    override val path: String = url

    /**
     * See [Sink.getFormat]
     */
    override val format: String = "flv"

    /**
     * See [Sink.getOptions]
     */
    override val options: String = "-maxrate ${streamingMaxBitrate}k -bufsize ${streamingBufSize}k"
}