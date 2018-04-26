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
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.executor.impl.LinuxFfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.impl.MacFfmpegExecutor
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import java.util.logging.Logger

/**
 * [FfmpegCapturer] is responsible for launching ffmpeg, capturing from the
 * configured audio and video devices, and writing to the given [Sink]
 */
class FfmpegCapturer : Capturer {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val ffmpegExecutor: FfmpegExecutor

    init
    {
        logger.debug("OS: ${System.getProperty("os.name")}")
        ffmpegExecutor = when (System.getProperty("os.name")) {
            "Mac OS X" -> MacFfmpegExecutor()
            "Linux" -> LinuxFfmpegExecutor()
            else -> throw UnsupportedOsException()
        }
    }

    override fun start(sink: Sink): Boolean {
        if (!ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)) {
            return false
        }
        // Now make sure ffmpeg is actually healthy before returning that start
        // was successful in case it starts up (and stays alive) but fails to
        // start encoding successfully
        for (i in 1..15) {
            if (isHealthy()) {
                return true
            }
            Thread.sleep(1000)
        }
        logger.error("Ffmpeg started up but did not start encoding after 15 tries, giving up")
        return false
    }

    override fun isHealthy(): Boolean = ffmpegExecutor.isHealthy()

    override fun getExitCode(): Int? = ffmpegExecutor.getExitCode()

    override fun stop() {
        ffmpegExecutor.stopFfmpeg()
    }
}
