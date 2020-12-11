/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */
package org.jitsi.jibri.capture.ffmpeg

import org.jitsi.jibri.UnsupportedOsException
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.OsType
import org.jitsi.jibri.util.getOsType
import java.util.logging.FileHandler

/**
 * Parameters which will be passed to ffmpeg
 */
data class FfmpegParams(
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

fun getFfmpegCommandLinux(ffmpegParams: FfmpegParams, sink: Sink): List<String> {
    return listOf(
        "ffmpeg", "-y", "-v", "info",
        "-f", "x11grab",
        "-draw_mouse", "0",
        "-r", ffmpegParams.framerate.toString(),
        "-s", ffmpegParams.resolution,
        "-thread_queue_size", ffmpegParams.queueSize.toString(),
        "-i", ":0.0+0,0",
        "-f", "alsa",
        "-thread_queue_size", ffmpegParams.queueSize.toString(),
        "-i", "plug:bsnoop",
        "-acodec", "aac", "-strict", "-2", "-ar", "44100", "-b:a", "128k",
        "-af", "aresample=async=1",
        "-c:v", "libx264", "-preset", ffmpegParams.videoEncodePreset,
        *sink.options, "-pix_fmt", "yuv420p", "-r", ffmpegParams.framerate.toString(),
        "-crf", ffmpegParams.h264ConstantRateFactor.toString(),
        "-g", ffmpegParams.gopSize.toString(), "-tune", "zerolatency",
        "-f", sink.format, sink.path
    )
}

fun getFfmpegCommandMac(ffmpegParams: FfmpegParams, sink: Sink): List<String> {
    return listOf(
        "ffmpeg", "-y", "-v", "info",
        "-thread_queue_size", ffmpegParams.queueSize.toString(),
        "-f", "avfoundation",
        "-framerate", ffmpegParams.framerate.toString(),
        "-video_size", ffmpegParams.resolution,
        // Note the values passed here will need to be changed based on the output of
        // ffmpeg -f avfoundation -list_devices true -i ""
        // Make them configurable?
        "-i", "2:1",
        "-vsync", "2",
        "-acodec", "aac", "-strict", "-2", "-ar", "44100", "-b:a", "128k",
        "-c:v", "libx264", "-preset", ffmpegParams.videoEncodePreset,
        *sink.options, "-pix_fmt", "yuv420p", "-crf", ffmpegParams.h264ConstantRateFactor.toString(),
        "-g", ffmpegParams.gopSize.toString(), "-tune", "zerolatency",
        "-f", sink.format, sink.path
    )
}

// TODO: put the above commands in config?  As it is there's no way to override them without recompiling
fun getFfmpegCommand(sink: Sink, osDetector: () -> OsType = ::getOsType): List<String> {
    return when (val os = osDetector()) {
        is OsType.Mac -> getFfmpegCommandMac(FfmpegParams(), sink)
        is OsType.Linux -> getFfmpegCommandLinux(FfmpegParams(), sink)
        is OsType.Unsupported -> throw UnsupportedOsException("Ffmpeg not supported on ${os.osStr}")
    }
}

/**
 * A distinct [FileHandler] so that we can configure the file
 * Ffmpeg logs to separately in the logging config
 */
object FfmpegFileHandler : FileHandler()
