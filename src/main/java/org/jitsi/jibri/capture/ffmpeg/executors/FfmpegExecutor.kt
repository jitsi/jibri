package org.jitsi.jibri.capture.ffmpeg.executors

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.MonitorableProcess

interface FfmpegExecutor : MonitorableProcess {
    fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink)
    fun stopFfmpeg()
}