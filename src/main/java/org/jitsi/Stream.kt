package org.jitsi

import java.net.URI

class Stream(val url: String) : Sink
{
    public override fun getPath(): String? = url

    public override fun finalize(): Unit
    {
        //TODO: anything to do here?
    }
}