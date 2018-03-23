package org.jitsi.jibri.capture.ffmpeg

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.executor.impl.LinuxFfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.impl.MacFfmpegExecutor
import org.jitsi.jibri.util.extensions.debug
import java.util.logging.Logger

/**
 * [FfmpegCapturer] is responsible for launching ffmpeg, capturing from the
 * configured audio and video devices, and writing to the given [Sink]
 */
class FfmpegCapturer : Capturer {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    val ffmpegExecutor: FfmpegExecutor

    init
    {
        logger.debug("OS: ${System.getProperty("os.name")}")
        ffmpegExecutor = when (System.getProperty("os.name")) {
            "Mac OS X" -> MacFfmpegExecutor()
            "Linux" -> LinuxFfmpegExecutor()
            else -> throw UnsupportedOsException()
        }
    }

    override fun start(sink: Sink): Boolean {
        return ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
    }

    override fun isHealthy(): Boolean = ffmpegExecutor.isHealthy()

    override fun getExitCode(): Int? = ffmpegExecutor.getExitCode()

    override fun stop() {
        ffmpegExecutor.stopFfmpeg()
    }
}
