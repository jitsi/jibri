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

import io.kotlintest.matchers.contain
import io.kotlintest.matchers.haveKey
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class OutputParserTest : StringSpec({
    val parser = OutputParser()
    fun verifyHelper(expectedValues: Map<String, Any>, actualValues: Map<String, Any>) {
        actualValues.size shouldBe expectedValues.size
        expectedValues.forEach { (field, value) ->
            actualValues should haveKey(field)
            actualValues should contain(field, value)
        }
    }

    "parsing of a normal output line should parse all values" {
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

    "parsing of a 'past duration' line should parse a warning line" {
        val outputLine = "Past duration 0.622368 too large"
        val result = parser.parse(outputLine)
        result.size shouldBe 1
        result should haveKey(WARNING_KEY)
        result should contain(WARNING_KEY, outputLine as Any)
    }

    "an unknown line should result in no fields parsed" {
        val outputLine = "wrong line"
        val result = parser.parse(outputLine)
        result.size shouldBe 0
    }
})
