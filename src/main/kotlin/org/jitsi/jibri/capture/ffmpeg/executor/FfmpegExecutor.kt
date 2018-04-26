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

package org.jitsi.jibri.capture.ffmpeg.executor

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.MonitorableProcess

/**
 * Paramaters which will be passed to ffmpeg
 */
data class FfmpegExecutorParams(
    val resolution: String = "1280x720",
    val framerate: Int = 30,
    val videoEncodePreset: String = "veryfast",
    val queueSize: Int = 4096,
    val streamingMaxBitrate: Int = 2976,
    val streamingBufSize: Int = streamingMaxBitrate * 2,
    // The range of the CRF scale is 0–51, where 0 is lossless,
    // 23 is the default, and 51 is worst quality possible. A lower value
    // generally leads to higher quality, and a subjectively sane range is
    // 17–28. Consider 17 or 18 to be visually lossless or nearly so;
    // it should look the same or nearly the same as the input but it
    // isn't technically lossless.
    // https://trac.ffmpeg.org/wiki/Encode/H.264#crf
    val h264ConstantRateFactor: Int = 25,
    val gopSize: Int = framerate * 2
)

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
    fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): Boolean

    /**
     * Shutdown ffmpeg gracefully (if possible)
     */
    fun stopFfmpeg()
}
