package org.jitsi.jibri.sink.impl

import org.jitsi.jibri.sink.Sink

class StreamSink(val url: String, val streamingMaxBitrate: Int, val streamingBufSize: Int) : Sink
{
    override fun getPath(): String? = url

    override fun getFormat(): String = "flv"

    override fun getOptions(): String = "-maxrate ${streamingMaxBitrate}k -bufsize ${streamingBufSize}k"
}