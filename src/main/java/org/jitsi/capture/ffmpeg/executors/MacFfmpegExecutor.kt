package org.jitsi.capture.ffmpeg.executors

import org.jitsi.sink.Sink
import java.util.concurrent.TimeUnit

class MacFfmpegExecutor : FfmpegExecutor
{
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
        println("launched ffmpeg, is it alive? ${currentFfmpegProc?.isAlive()}")
    }

    override fun isAlive(): Boolean = currentFfmpegProc?.isAlive ?: false

    override fun getExitCode(): Int? = currentFfmpegProc?.exitValue()

    override fun stopFfmpeg() {
        currentFfmpegProc?.pid().let {
            println("sending SIGINT to ffmpeg proc ${currentFfmpegProc?.pid()}")
            Runtime.getRuntime().exec("kill -s SIGINT ${currentFfmpegProc!!.pid()}")
        }
        currentFfmpegProc?.waitFor(10, TimeUnit.SECONDS)
        currentFfmpegProc?.isAlive.let {
            // This isn't great, as killing ffmpeg this way will corrupt
            // the entire recording (from what I've seen)
            currentFfmpegProc?.destroyForcibly()
        }
        println("FfmpegCapturer exited with ${currentFfmpegProc?.exitValue()}")
    }
}