package org.jitsi.jibri.capture.ffmpeg.util

import org.jitsi.jibri.util.bitrate
import org.jitsi.jibri.util.dataSize
import org.jitsi.jibri.util.decimal
import org.jitsi.jibri.util.oneOrMoreDigits
import org.jitsi.jibri.util.speed
import org.jitsi.jibri.util.timestamp
import org.jitsi.jibri.util.zeroOrMoreSpaces
import java.util.regex.Pattern

/**
 * Parses the stdout output of ffmpeg to check if it's working
 */
class OutputParser {
    /**
     * The regex for parsing the encoding output of ffmpeg, e.g.:
     * frame=   95 fps= 31 q=27.0 size=     584kB time=00:00:03.60 bitrate=1329.4kbits/s speed=1.19x
     */
    private val encodingLinePattern: Pattern
    private val encodingLineFields = listOf(
        Pair("frame", oneOrMoreDigits),
        Pair("fps", oneOrMoreDigits),
        Pair("q", decimal),
        Pair("size", dataSize),
        Pair("time", timestamp),
        Pair("bitrate", bitrate),
        Pair("speed", speed)
    )
    init {
        val encodingLineRegex = encodingLineFields
            // Format each field name and value to how they appear in the output line, with support for any number
            // of spaces in between (and name each regex group according to the field name)
            // <fieldName>= value
            .map { (fieldName, value) -> "$fieldName=${zeroOrMoreSpaces}(?<$fieldName>$value)${zeroOrMoreSpaces}" }
            // Concatenate all the individual fields into one regex pattern string
            .fold("") { pattern, currentField -> "$pattern$currentField" }
        encodingLinePattern = Pattern.compile(encodingLineRegex)
    }

    fun parse(outputLine: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val matcher = encodingLinePattern.matcher(outputLine)
        if (matcher.find()) {
            encodingLineFields.forEach {(fieldName, _) ->
                result.put(fieldName, matcher.group(fieldName))
            }
        }
        return result
    }
}