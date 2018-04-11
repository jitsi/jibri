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

import org.jitsi.jibri.capture.ffmpeg.util.ENCODING_KEY
import org.jitsi.jibri.capture.ffmpeg.util.OutputParser
import org.jitsi.jibri.capture.ffmpeg.util.WARNING_KEY
import org.jitsi.jibri.util.ProcessWrapper

enum class FfmpegStatus {
    HEALTHY,
    WARNING,
    ERROR
}

class FfmpegProcessWrapper(
    command: List<String>,
    environment: Map<String, String> = mapOf(),
    private val processBuilder: ProcessBuilder = ProcessBuilder()
) : ProcessWrapper<FfmpegStatus>(command, environment, processBuilder) {

    override fun getStatus(): Pair<FfmpegStatus, String> {
        val mostRecentLine = getMostRecentLine()
        val result = OutputParser.parse(mostRecentLine)
        val status = when {
            result.containsKey(ENCODING_KEY) -> FfmpegStatus.HEALTHY
            result.containsKey(WARNING_KEY) -> FfmpegStatus.WARNING
            else -> FfmpegStatus.ERROR
        }
        return Pair(status, mostRecentLine)
    }
}
