package org.jitsi.jibri.capture.ffmpeg.executors

import org.jitsi.jibri.sink.Sink

class LinuxFfmpegExecutor : FfmpegExecutor
{
//        val ffmpegCommandLinux = "" +
//        "ffmpeg -y -v info -f x11grab -draw_mouse 0 -r $framerate -s $resolution " +
//        "-thread_queue_size $queueSize -i ${display}.0+0,0 " +
//        "-f alsa -thread_queue_size $queueSize -i $audioInputDevice -acodec aac -strict -2 -ar 44100 " +
//        "-c:v libx264 -preset $videoEncodePreset $sinkOptions -pix_fmt yuv420p -r $framerate -crf $h264ConstantRateFactor -g $gopSize -tune zerolatency " +
//        "-f $format $sinkUri"

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getExitCode(): Int? {
        TODO()
    }

    override fun isAlive(): Boolean {
        TODO()
    }

    override fun stopFfmpeg() {
        TODO()
    }
}