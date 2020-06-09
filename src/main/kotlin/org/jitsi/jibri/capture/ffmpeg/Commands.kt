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
package org.jitsi.jibri.capture.ffmpeg

import org.jitsi.jibri.sink.Sink

fun getFfmpegCommandLinux(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): List<String> {
    return listOf(
        "ffmpeg", "-y", "-v", "info",
        "-f", "x11grab",
        "-draw_mouse", "0",
        "-r", ffmpegExecutorParams.framerate.toString(),
        "-s", ffmpegExecutorParams.resolution,
        "-thread_queue_size", ffmpegExecutorParams.queueSize.toString(),
        "-i", ":0.0+0,0",
        "-f", "alsa",
        "-thread_queue_size", ffmpegExecutorParams.queueSize.toString(),
        "-i", "plug:bsnoop",
        "-acodec", "aac", "-strict", "-2", "-ar", "44100", "-b:a", "128k",
        "-af", "aresample=async=1",
        "-c:v", "libx264", "-preset", ffmpegExecutorParams.videoEncodePreset,
        *sink.options, "-pix_fmt", "yuv420p", "-r", ffmpegExecutorParams.framerate.toString(),
        "-crf", ffmpegExecutorParams.h264ConstantRateFactor.toString(),
        "-g", ffmpegExecutorParams.gopSize.toString(), "-tune", "zerolatency",
        "-f", sink.format, sink.path
    )
}

fun getFfmpegCommandMac(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): List<String> {
    return listOf(
        "ffmpeg", "-y", "-v", "info",
        "-thread_queue_size", ffmpegExecutorParams.queueSize.toString(),
        "-f", "avfoundation",
        "-framerate", ffmpegExecutorParams.framerate.toString(),
        "-video_size", ffmpegExecutorParams.resolution,
        "-i", "0:0",
        "-vsync", "2",
        "-acodec", "aac", "-strict", "-2", "-ar", "44100", "-b:a 128k",
        "-c:v", "libx264", "-preset", ffmpegExecutorParams.videoEncodePreset,
        *sink.options, "-pix_fmt", "yuv420p", "-crf", ffmpegExecutorParams.h264ConstantRateFactor.toString(),
        "-g", ffmpegExecutorParams.gopSize.toString(), "-tune", "zerolatency",
        "-f", sink.format, sink.path
    )
}
