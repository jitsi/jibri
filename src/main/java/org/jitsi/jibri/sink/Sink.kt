package org.jitsi.jibri.sink

interface Sink
{
    fun getPath(): String?
    fun getFormat(): String?
    fun getOptions(): String
}