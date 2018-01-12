package org.jitsi.sink

interface Sink
{
    fun getPath(): String?
    fun getFormat(): String?
    fun getOptions(): String
    fun finalize()
}