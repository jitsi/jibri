package org.jitsi.jibri.capture.ffmpeg.util

import org.jitsi.jibri.util.oneOrMoreNonSpaces
import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

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
    private val ffmpegOutputField = """$zeroOrMoreSpaces($oneOrMoreNonSpaces)$zeroOrMoreSpaces=$zeroOrMoreSpaces($oneOrMoreNonSpaces)"""

    /**
     * In addition to the above, sometimes ffmpeg will print a 'warning'-type message, like the following:
     * Past duration 0.622368 too large
     * What else? TODO
     */

    fun parse(outputLine: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        // First parse the output line as generic field and value fields
        val matcher = Pattern.compile(ffmpegOutputField).matcher(outputLine)
        while (matcher.find()) {
            val fieldName = matcher.group(1).trim()
            val fieldValue = matcher.group(2).trim()
            result.put(fieldName, fieldValue)
        }

        return result
    }
}