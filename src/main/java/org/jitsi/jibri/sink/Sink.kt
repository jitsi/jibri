package org.jitsi.jibri.sink

/**
 * [Sink] describes a class which data will be 'written to'.  It contains
 * a destionation (via [getPath]), a format (via [getFormat]) and a set
 * of options which each [Sink] implementation may provide.
 * TODO: currently this is modeled as generic, but really it's an
 * "FfmpegSink", so maybe it should be named as such?
 */
interface Sink
{
    /**
     * Return the path to which this [Sink] has been designated to write
     */
    fun getPath(): String?

    /**
     * Return the format of the container which this [Sink] will use
     */
    fun getFormat(): String?

    /**
     * Return any ffmpeg command-line options this [Sink] requires
     */
    fun getOptions(): String
}