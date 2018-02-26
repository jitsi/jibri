package org.jitsi.jibri.capture.ffmpeg.util

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.testng.annotations.Test

import org.testng.annotations.BeforeMethod

class OutputParserTest {
    lateinit var parser: OutputParser

    @BeforeMethod
    fun setUp() {
        parser = OutputParser()
    }

    fun verifyHelper(expectedValues: Map<String, Any>, actualValues: Map<String, Any>) {
        assertEquals(actualValues.size, expectedValues.size)
        expectedValues.forEach { (field, value) ->
            assertTrue(actualValues.contains(field))
            assertEquals(actualValues[field], value)
        }
    }

    @Test
    fun `test basic parse`() {
        val outputLine = "frame=   95 fps= 31 q=27.0 size=     584kB time=00:00:03.60 bitrate=1329.4kbits/s speed=1.19x"
        val expectedValues = mapOf(
            "frame" to "95",
            "fps" to "31",
            "q" to "27.0",
            "size" to "584kB",
            "time" to "00:00:03.60",
            "bitrate" to "1329.4kbits/s",
            "speed" to "1.19x"
        )

        val result = parser.parse(outputLine)
        verifyHelper(expectedValues, result)
    }

    @Test
    fun `test parse non-decimal speed field`() {
        val outputLine = "frame=   95 fps= 31 q=27.0 size=     584kB time=00:00:03.60 bitrate=1329.4kbits/s speed=1x"
        val expectedValues = mapOf(
            "frame" to "95",
            "fps" to "31",
            "q" to "27.0",
            "size" to "584kB",
            "time" to "00:00:03.60",
            "bitrate" to "1329.4kbits/s",
            "speed" to "1x"
        )

        val result = parser.parse(outputLine)
        verifyHelper(expectedValues, result)
    }

    @Test
    fun `test failed parse`() {
        val outputLine = "wrong line"
        val result = parser.parse(outputLine)

        assertEquals(0, result.size)
    }
}