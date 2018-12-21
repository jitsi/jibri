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

import org.jitsi.jibri.util.decimal
import org.jitsi.jibri.util.oneOrMoreDigits
import org.jitsi.jibri.util.oneOrMoreNonSpaces
import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

enum class ErrorScope {
    SESSION,
    SYSTEM
}

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
class FfmpegErrorStatus(val errorScope: ErrorScope, detail: String) : FfmpegOutputStatus(OutputLineClassification.ERROR, detail)

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
        private const val ffmpegOutputFieldName = oneOrMoreNonSpaces
        private const val ffmpegOutputFieldValue = oneOrMoreNonSpaces
        private const val ffmpegOutputField =
            "$zeroOrMoreSpaces($ffmpegOutputFieldName)$zeroOrMoreSpaces=$zeroOrMoreSpaces($ffmpegOutputFieldValue)"

        private const val ffmpegEncodingLine = "($ffmpegOutputField)+$zeroOrMoreSpaces"
        private const val ffmpegExitedLine = "Exiting.*signal$zeroOrMoreSpaces($oneOrMoreDigits).*"

        /**
         * ffmpeg bad rtmp url line
         */
        private const val badRtmpUrl = "rtmp://.*Input/output error"

        /**
         * Errors are done a bit differently, as different errors have different scopes.  For example,
         * a bad RTMP url is an error that only affects this session but an error about running out of
         * disk space affects the entire system.  This map associates the regex of the ffmpeg error output
         * to its scope.
         */
        private val errorTypes = mapOf(
            badRtmpUrl to ErrorScope.SESSION
        )

        /**
         * Parse [outputLine], a line of output from Ffmpeg, into an [FfmpegOutputStatus]
         */
        fun parse(outputLine: String): FfmpegOutputStatus {
            // First we'll check if the output represents that ffmpeg has exited
            val exitedMatcher = Pattern.compile(ffmpegExitedLine).matcher(outputLine)
            if (exitedMatcher.matches()) {
                val signal = exitedMatcher.group(1).toInt()
                when (signal) {
                    // 2 is the signal we pass to stop ffmpeg
                    2 -> {
                        return FfmpegOutputStatus(OutputLineClassification.FINISHED, outputLine)
                    }
                    else -> {
                        return FfmpegErrorStatus(ErrorScope.SESSION, outputLine)
                    }
                }
            }
            // Check if the output is a normal, encoding output
            val encodingLineMatcher = Pattern.compile(ffmpegEncodingLine).matcher(outputLine)
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
            for ((errorLine, errorScope) in errorTypes) {
                val errorMatcher = Pattern.compile(errorLine).matcher(outputLine)
                if (errorMatcher.matches()) {
                    return FfmpegErrorStatus(errorScope, outputLine)
                }
            }
            return FfmpegOutputStatus(OutputLineClassification.UNKNOWN, outputLine)
        }
    }
}
