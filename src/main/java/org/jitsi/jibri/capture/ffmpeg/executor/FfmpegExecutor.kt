package org.jitsi.jibri.capture.ffmpeg.executor

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.MonitorableProcess

/**
 * [FfmpegExecutor] is responsible for executing ffmpeg.  This interface
 * allows different executors to be implemted so that settings may be varied
 * per platform
 */
interface FfmpegExecutor : MonitorableProcess {
    /**
     * Launch ffmpeg with the given [FfmpegExecutorParams] and using
     * the given [Sink]
     */
    fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink)

    /**
     * Shutdown ffmpeg gracefully (if possible)
     */
    fun stopFfmpeg()
}
