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

import org.jitsi.jibri.capture.ffmpeg.util.FfmpegStatus
import org.jitsi.jibri.capture.ffmpeg.util.getFfmpegStatus
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import java.util.logging.Logger

fun isFfmpegHealthy(process: ProcessWrapper?, logger: Logger): Boolean {
    if (process == null) {
        return false
    }
    val (status, mostRecentOutput) = getFfmpegStatus(process)
    return when (status) {
        FfmpegStatus.HEALTHY -> {
            logger.debug("Ffmpeg appears healthy: $mostRecentOutput")
            true
        }
        FfmpegStatus.WARNING -> {
            logger.info("Ffmpeg is encoding, but issued a warning: $mostRecentOutput")
            true
        }
        FfmpegStatus.ERROR -> {
            logger.error("Ffmpeg is running but doesn't appear to be encoding: $mostRecentOutput")
            false
        }
        FfmpegStatus.EXITED -> {
            logger.error("Ffmpeg exited with code ${process.exitValue}.  Its most recent output was $mostRecentOutput")
            false
        }
    }
}
