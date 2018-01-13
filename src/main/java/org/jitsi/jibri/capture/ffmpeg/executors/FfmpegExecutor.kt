package org.jitsi.jibri.capture.ffmpeg.executors

import org.jitsi.jibri.sink.Sink

interface FfmpegExecutor {
    fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink)
    fun isAlive(): Boolean
    fun getExitCode(): Int?
    fun stopFfmpeg()
}