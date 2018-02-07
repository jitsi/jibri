package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.debug
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Ffmpeg execution specific to Linux
 */
class LinuxFfmpegExecutor : FfmpegExecutor
{
    private val logger = Logger.getLogger(this::class.simpleName)
    var currentFfmpegProc: Process? = null


    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink) {
        val ffmpegCommandLinux = """
        ffmpeg -y -v info
        -f x11grab
        -draw_mouse 0
        -r ${ffmpegExecutorParams.framerate}
        -s ${ffmpegExecutorParams.resolution}
        -thread_queue_size ${ffmpegExecutorParams.queueSize}
        -i ${ffmpegExecutorParams.display}.0+0,0
        -f alsa
        -thread_queue_size ${ffmpegExecutorParams.queueSize}
        -i ${ffmpegExecutorParams.audioInputDevice}
        -acodec aac -strict -2 -ar 44100
        -c:v libx264 -preset ${ffmpegExecutorParams.videoEncodePreset}
        ${sink.options} -pix_fmt yuv420p -r ${ffmpegExecutorParams.framerate}
         -crf ${ffmpegExecutorParams.h264ConstantRateFactor}
         -g ${ffmpegExecutorParams.gopSize} -tune zerolatency
        -f ${sink.format} ${sink.path}
        """.trimIndent().replace("\n", " ")
        val pb = ProcessBuilder(ffmpegCommandLinux.split(" "))
        pb.redirectOutput(File("/tmp/ffmpeg.out"))
        pb.redirectError(File("/tmp/ffmpeg.out"))

        logger.info("running ffmpeg command:\n $ffmpegCommandLinux")
        //currentFfmpegProc = Runtime.getRuntime().exec(ffmpegCommandMac)
        currentFfmpegProc = pb.start()
        logger.debug("launched ffmpeg, is it alive? ${currentFfmpegProc?.isAlive()}")
    }

    override fun isAlive(): Boolean = currentFfmpegProc?.isAlive ?: false

    override fun getExitCode(): Int? = currentFfmpegProc?.exitValue()

    override fun stopFfmpeg() {
        currentFfmpegProc?.pid()?.let {
            logger.info("sending SIGINT to ffmpeg proc ${it}")
            Runtime.getRuntime().exec("kill -s SIGINT ${it}")
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