package org.jitsi.capture.ffmpeg.executors

import org.jitsi.sink.Sink

interface FfmpegExecutor {
    fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink)
    fun isAlive(): Boolean
    fun getExitCode(): Int?
    fun stopFfmpeg()
}