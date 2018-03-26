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
    fun `test parse line with extra values`() {
        val outputLine = "frame=   95 fps= 31 q=27.0 size=     584kB time=00:00:03.60 bitrate=1329.4kbits/s dup=0 drop=1 speed=1x"
        val expectedValues = mapOf(
            "frame" to "95",
            "fps" to "31",
            "q" to "27.0",
            "size" to "584kB",
            "time" to "00:00:03.60",
            "bitrate" to "1329.4kbits/s",
            "speed" to "1x",
            "dup" to "0",
            "drop" to "1"
        )

        val result = parser.parse(outputLine)
        verifyHelper(expectedValues, result)
    }

    @Test
    fun `test parse line with different order`() {
        val outputLine = "fps= 31 frame=   95 q=27.0 size=     584kB time=00:00:03.60 bitrate=1329.4kbits/s dup=0 drop=1 speed=1x"
        val expectedValues = mapOf(
            "frame" to "95",
            "fps" to "31",
            "q" to "27.0",
            "size" to "584kB",
            "time" to "00:00:03.60",
            "bitrate" to "1329.4kbits/s",
            "speed" to "1x",
            "dup" to "0",
            "drop" to "1"
        )

        val result = parser.parse(outputLine)
        verifyHelper(expectedValues, result)
    }

    @Test
    fun `test random fields`() {
        val outputLine = "frame=   95 dup=0 drop=1 somenewfield=42 someotherfield=xy"
        val expectedValues = mapOf(
            "frame" to "95",
            "dup" to "0",
            "drop" to "1",
            "somenewfield" to "42",
            "someotherfield" to "xy"
        )

        val result = parser.parse(outputLine)
        verifyHelper(expectedValues, result)
    }

    @Test
    fun `test past duration line`() {
        val outputLine = "Past duration 0.622368 too large"

        val result = parser.parse(outputLine)
        assertEquals(1, result.size)
        assertTrue(result.containsKey(WARNING_KEY))
    }

    @Test
    fun `test failed parse`() {
        val outputLine = "wrong line"
        val result = parser.parse(outputLine)

        assertEquals(0, result.size)
    }
}
