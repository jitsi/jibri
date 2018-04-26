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

package org.jitsi.jibri.capture.ffmpeg.util

import org.jitsi.jibri.util.decimal
import org.jitsi.jibri.util.oneOrMoreNonSpaces
import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

/**
 * The key (from the set of key, value pairs we parse
 * from ffmpeg's stdout output) that corresponds to
 * successful, ongoing encoding from ffmpeg. i.e.:
 * frame=123
 */
const val ENCODING_KEY = "frame"
/**
 * The key we use when inserting a warning output line
 * from ffmpeg into the map of parsed key, value pairs
 * from parsing ffmpeg's output
 */
const val WARNING_KEY = "warning"

/**
 * Parses the stdout output of ffmpeg to check if it's working
 */
class OutputParser {
    companion object {
        /**
         * Ffmpeg prints to stdout while its running with a status of its current job.
         * For the most part, it uses the following format:
         * fieldName=fieldValue fieldName2=fieldValue fieldName3=fieldValue...
         * where any amount spaces can be inserted anywhere in that pattern (except for within
         * a fieldName or fieldValue).  This pattern will parse all fields from an ffmpeg output
         * string
         */
        private const val ffmpegOutputField =
        // The key
        "$zeroOrMoreSpaces($oneOrMoreNonSpaces)$zeroOrMoreSpaces" +
        "=" +
        // The value
        "$zeroOrMoreSpaces($oneOrMoreNonSpaces)"

        /**
         * ffmpeg past duration warning line
         */
        private const val ffmpegPastDuration = "Past duration $decimal too large"

        /**
         * Ffmpeg warning lines that denote a 'hiccup' (but not a failure)
         */
        private val warningLines = listOf(
            ffmpegPastDuration
        )

        fun parse(outputLine: String): Map<String, Any> {
            val result = mutableMapOf<String, Any>()

            // First parse the output line as generic field and value fields
            val matcher = Pattern.compile(ffmpegOutputField).matcher(outputLine)
            while (matcher.find()) {
                val fieldName = matcher.group(1).trim()
                val fieldValue = matcher.group(2).trim()
                result[fieldName] = fieldValue
            }
            for (warningLine in warningLines) {
                val warningMatcher = Pattern.compile(ffmpegPastDuration).matcher(outputLine)
                if (warningMatcher.matches()) {
                    result[WARNING_KEY] = outputLine
                    break
                }
            }

            return result
        }

        fun isHealthy(outputLine: String): Boolean {
            val parsedOutputLine = parse(outputLine)
            return parsedOutputLine.containsKey(ENCODING_KEY) || parsedOutputLine.containsKey(WARNING_KEY)
        }
    }
}
