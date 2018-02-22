package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.pid
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

abstract class AbstractFfmpegExecutor : FfmpegExecutor {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    var currentFfmpegProc: Process? = null

    /**
     * Get the command to use to launch ffmpeg
     */
    protected abstract fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink) {
        val command = getFfmpegCommand(ffmpegExecutorParams, sink)

        val pb = ProcessBuilder(command.split(" "))
        pb.redirectErrorStream(true)
        pb.redirectOutput(File("/tmp/ffmpeg.out"))

        logger.info("running ffmpeg command:\n $command")
        currentFfmpegProc = pb.start()
        logger.debug("launched ffmpeg, is it alive? ${currentFfmpegProc?.isAlive}")
    }

    override fun getExitCode(): Int? = currentFfmpegProc?.exitValue()

    override fun isAlive(): Boolean = currentFfmpegProc?.isAlive == true

    override fun stopFfmpeg() {
        currentFfmpegProc?.let {
            val pid = pid(it)
            logger.info("sending SIGINT to ffmpeg proc $pid")
            Runtime.getRuntime().exec("kill -s SIGINT $pid")
        } ?: run {
            logger.info("stopFfmpeg: ffmpeg had already exited")
        }
        currentFfmpegProc?.waitFor(10, TimeUnit.SECONDS)
        currentFfmpegProc?.isAlive.let {
            // This isn't great, as killing ffmpeg this way will corrupt
            // the entire recording (from what I've seen)
            currentFfmpegProc?.destroyForcibly()
        }
        logger.info("FfmpegCapturer exited with ${currentFfmpegProc?.exitValue()}")
    }
}
