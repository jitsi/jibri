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

import org.jitsi.jibri.error.JibriError
import org.jitsi.jibri.util.ONE_OR_MORE_DIGITS
import org.jitsi.jibri.util.ONE_OR_MORE_NON_SPACES
import org.jitsi.jibri.util.ZERO_OR_MORE_SPACES
import java.util.regex.Pattern

enum class OutputLineClassification {
    UNKNOWN,
    ENCODING,
    FINISHED,
    ERROR
}

/**
 * Represents a parsed line of ffmpeg's stdout output.
 */
open class FfmpegOutputStatus(val lineType: OutputLineClassification, val detail: String = "") {
    override fun toString(): String {
        return "Line type: $lineType, detail: $detail"
    }
}

/**
 * Represents a line of ffmpeg output that indicated there was a warning.
 */
open class FfmpegErrorStatus(val error: JibriError) : FfmpegOutputStatus(OutputLineClassification.ERROR, error.detail)

/**
 * Represents a line of ffmpeg output that indicated a bad RTMP URL
 */
class BadRtmpUrlStatus(outputLine: String) : FfmpegErrorStatus(BadRtmpUrl(outputLine))

class BrokenPipeStatus(outputLine: String) : FfmpegErrorStatus(BrokenPipe(outputLine))

/**
 * Ffmpeg quit due to a signal other than what we sent to it
 */
class FfmpegUnexpectedSignalStatus(outputLine: String) : FfmpegErrorStatus(FfmpegUnexpectedSignal(outputLine))

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
        private const val FFMPEG_OUTPUT_FIELD_NAME = ONE_OR_MORE_NON_SPACES
        private const val FFMPEG_OUTPUT_FIELD_VALUE = ONE_OR_MORE_NON_SPACES
        private const val FFMPEG_OUTPUT_FIELD =
            "$ZERO_OR_MORE_SPACES($FFMPEG_OUTPUT_FIELD_NAME)$ZERO_OR_MORE_SPACES=" +
                "$ZERO_OR_MORE_SPACES($FFMPEG_OUTPUT_FIELD_VALUE)"

        private const val FFMPEG_ENCODING_LINE = "($FFMPEG_OUTPUT_FIELD)+$ZERO_OR_MORE_SPACES"
        private const val FFMPEG_EXITED_LINE = "Exiting.*signal$ZERO_OR_MORE_SPACES($ONE_OR_MORE_DIGITS).*"
        private const val BAD_RTMP_URL = "rtmp://.*Input/output error"
        private const val BROKEN_PIPE = ".*Broken pipe.*"

        /**
         * Errors are done a bit differently, as different errors have different scopes.  For example,
         * a bad RTMP url is an error that only affects this session but an error about running out of
         * disk space affects the entire system.  This map associates the regex of the ffmpeg error output
         * to a function which takes in the output line and returns an [FfmpegErrorStatus]
         */
        private val errorTypes = mapOf<String, (String) -> FfmpegErrorStatus>(
            BAD_RTMP_URL to ::BadRtmpUrlStatus,
            BROKEN_PIPE to ::BrokenPipeStatus
        )

        /**
         * Parse [outputLine], a line of output from Ffmpeg, into an [FfmpegOutputStatus]
         */
        fun parse(outputLine: String): FfmpegOutputStatus {
            // First we'll check if the output represents that ffmpeg has exited
            val exitedMatcher = Pattern.compile(FFMPEG_EXITED_LINE).matcher(outputLine)
            if (exitedMatcher.matches()) {
                return when (exitedMatcher.group(1).toInt()) {
                    // 2 is the signal we pass to stop ffmpeg
                    2 -> FfmpegOutputStatus(OutputLineClassification.FINISHED, outputLine)
                    else -> FfmpegUnexpectedSignalStatus(outputLine)
                }
            }
            // Check if the output is a normal, encoding output
            val encodingLineMatcher = Pattern.compile(FFMPEG_ENCODING_LINE).matcher(outputLine)
            if (encodingLineMatcher.matches()) {
//                val encodingFieldsMatcher = Pattern.compile(ffmpegOutputField).matcher(outputLine)
//                val fields = mutableMapOf<String, Any>()
//                while (encodingFieldsMatcher.find()) {
//                    val fieldName = encodingFieldsMatcher.group(1).trim()
//                    val fieldValue = encodingFieldsMatcher.group(2).trim()
//                    fields[fieldName] = fieldValue
//                }
                return FfmpegOutputStatus(OutputLineClassification.ENCODING, outputLine)
            }
            // Now we'll look for error output
            for ((errorLine, createError) in errorTypes) {
                val errorMatcher = Pattern.compile(errorLine).matcher(outputLine)
                if (errorMatcher.matches()) {
                    return createError(outputLine)
                }
            }
            return FfmpegOutputStatus(OutputLineClassification.UNKNOWN, outputLine)
        }
    }
}
