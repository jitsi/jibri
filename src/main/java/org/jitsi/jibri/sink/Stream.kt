package org.jitsi.jibri.sink

class Stream(val url: String, val streamingMaxBitrate: Int, val streamingBufSize: Int) : Sink
{
    override fun getPath(): String? = url

    override fun getFormat(): String = "flv"

    override fun getOptions(): String = "-maxrate ${streamingMaxBitrate}k -bufsize ${streamingBufSize}k"

    override fun finalize(): Unit
    {
        //TODO: anything to do here?
    }
}