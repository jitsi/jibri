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

import org.jitsi.jibri.BadRtmpUrl
import org.jitsi.jibri.BrokenPipe
import org.jitsi.jibri.util.oneOrMoreNonSpaces
import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

private const val ffmpegOutputFieldName = oneOrMoreNonSpaces
private const val ffmpegOutputFieldValue = oneOrMoreNonSpaces
private const val ffmpegOutputField =
    "$zeroOrMoreSpaces($ffmpegOutputFieldName)$zeroOrMoreSpaces=$zeroOrMoreSpaces($ffmpegOutputFieldValue)"

private val badRtmpUrl = Pattern.compile("rtmp://.*Input/output error")
private val brokenPipe = Pattern.compile(".*Broken pipe.*")

private val ffmpegEncodingLine = Pattern.compile("($ffmpegOutputField)+$zeroOrMoreSpaces")

class OutputParser {
    companion object {
        fun checkForErrors(ffmpegOutputLine: String) {
            if (badRtmpUrl.matcher(ffmpegOutputLine).matches()) {
                throw BadRtmpUrl("Bad RTMP url: $ffmpegOutputLine")
            }
            if (brokenPipe.matcher(ffmpegOutputLine).matches()) {
                throw BrokenPipe("Broken pipe: $ffmpegOutputLine")
            }
        }
        fun isEncoding(ffmpegOutputLine: String): Boolean {
            return ffmpegEncodingLine.matcher(ffmpegOutputLine).matches()
        }
    }
}
