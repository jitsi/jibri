package org.jitsi.jibri.capture.ffmpeg.executors

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.debug
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class MacFfmpegExecutor : FfmpegExecutor
{
    private val logger = Logger.getLogger(this::class.simpleName)
    var currentFfmpegProc: Process? = null

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink)
    {
        val ffmpegCommandMac = "" +
                "ffmpeg -y -v info " +
                "-thread_queue_size ${ffmpegExecutorParams.queueSize} " +
                "-f avfoundation " +
                "-r ${ffmpegExecutorParams.framerate} " +
                "-i 0:0 " +
                "-s ${ffmpegExecutorParams.resolution} " +
                "-acodec aac -strict -2 -ar 44100 " +
                "-c:v libx264 -preset ${ffmpegExecutorParams.videoEncodePreset} " +
                "${sink.getOptions()} -pix_fmt yuv420p -crf ${ffmpegExecutorParams.h264ConstantRateFactor} " +
                "-g ${ffmpegExecutorParams.gopSize} -tune zerolatency " +
                "-f ${sink.getFormat()} ${sink.getPath()}"

        currentFfmpegProc = Runtime.getRuntime().exec(ffmpegCommandMac)
        logger.debug("launched ffmpeg, is it alive? ${currentFfmpegProc?.isAlive()}")
    }

    override fun isAlive(): Boolean = currentFfmpegProc?.isAlive ?: false

    override fun getExitCode(): Int? = currentFfmpegProc?.exitValue()

    override fun stopFfmpeg() {
        currentFfmpegProc?.pid()?.let {
            logger.info("sending SIGINT to ffmpeg proc ${currentFfmpegProc?.pid()}")
            Runtime.getRuntime().exec("kill -s SIGINT ${currentFfmpegProc!!.pid()}")
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