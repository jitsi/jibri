/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.sink.Sink

/**
 * Ffmpeg execution specific to Linux
 */
class LinuxFfmpegExecutor : AbstractFfmpegExecutor() {
    override fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String {
        return """
            ffmpeg -y -v info
            -f x11grab
            -draw_mouse 0
            -r ${ffmpegExecutorParams.framerate}
            -s ${ffmpegExecutorParams.resolution}
            -thread_queue_size ${ffmpegExecutorParams.queueSize}
            -i :0.0+0,0
            -f alsa
            -thread_queue_size ${ffmpegExecutorParams.queueSize}
            -i hw:0,1,0
            -acodec aac -strict -2 -ar 44100
            -c:v libx264 -preset ${ffmpegExecutorParams.videoEncodePreset}
            ${sink.options} -pix_fmt yuv420p -r ${ffmpegExecutorParams.framerate}
            -crf ${ffmpegExecutorParams.h264ConstantRateFactor}
            -g ${ffmpegExecutorParams.gopSize} -tune zerolatency
            -f ${sink.format} ${sink.path}
        """.trimIndent().replace("\n", " ")
    }
}
