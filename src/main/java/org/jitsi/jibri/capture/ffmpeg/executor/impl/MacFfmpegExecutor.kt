package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.sink.Sink

/**
 * Ffmpeg execution specific to Mac OS
 */
class MacFfmpegExecutor : AbstractFfmpegExecutor()
{
    override fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String {
        return """
            ffmpeg -y -v info
            -thread_queue_size ${ffmpegExecutorParams.queueSize}
            -f avfoundation
            -framerate ${ffmpegExecutorParams.framerate}
            -video_size ${ffmpegExecutorParams.resolution}
            -i 0:0
            -vsync 2
            -acodec aac -strict -2 -ar 44100
            -c:v libx264 -preset ${ffmpegExecutorParams.videoEncodePreset}
            ${sink.options} -pix_fmt yuv420p -crf ${ffmpegExecutorParams.h264ConstantRateFactor}
            -g ${ffmpegExecutorParams.gopSize} -tune zerolatency
            -f ${sink.format} ${sink.path}
        """.trimIndent().replace("\n", " ")
    }
}