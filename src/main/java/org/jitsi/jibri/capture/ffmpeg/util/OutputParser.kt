package org.jitsi.jibri.capture.ffmpeg.util

import org.jitsi.jibri.util.decimal
import org.jitsi.jibri.util.oneOrMoreNonSpaces
import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

const val ENCODING_KEY = "frame"
const val WARNING_KEY = "warning"

/**
 * Parses the stdout output of ffmpeg to check if it's working
 */
class OutputParser {
    /**
     * Ffmpeg prints to stdout while its running with a status of its current job.
     * For the most part, it uses the following format:
     * fieldName=fieldValue fieldName2=fieldValue fieldName3=fieldValue...
     * where any amount spaces can be inserted anywhere in that pattern (except for within
     * a fieldName or fieldValue).  This pattern will parse all fields from an ffmpeg output
     * string
     */
    private val ffmpegOutputField =
        // The key
        "$zeroOrMoreSpaces($oneOrMoreNonSpaces)$zeroOrMoreSpaces" +
        "=" +
        // The value
        "$zeroOrMoreSpaces($oneOrMoreNonSpaces)"

    /**
     * ffmpeg past duration warning line
     */
    private val ffmpegPastDuration = "Past duration $decimal too large"

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
}
