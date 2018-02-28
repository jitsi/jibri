package org.jitsi.jibri.capture.ffmpeg.util

import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

/**
 * Parses the stdout output of ffmpeg to check if it's working
 */
class OutputParser {
    /**
     * Ffmpeg outputs its data (as far as we know) in the following format:
     * fieldName=fieldValue
     * where any amount spaces can be inserted anywhere in that pattern (except for within
     * a fieldName or fieldValue).  This pattern will parse all fields from an ffmpeg output
     * string
     */
    private val ffmpegOutputField = """$zeroOrMoreSpaces(\S+)$zeroOrMoreSpaces=$zeroOrMoreSpaces(\S+)"""

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