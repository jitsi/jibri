package org.jitsi

import java.net.URI

interface Sink
{
    public fun getPath(): String?
    public fun finalize(): Unit
}