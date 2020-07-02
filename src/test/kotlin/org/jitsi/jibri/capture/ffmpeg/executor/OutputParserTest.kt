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

package org.jitsi.jibri.capture.ffmpeg.executor

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.beInstanceOf
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.capture.ffmpeg.FfmpegErrorStatus
import org.jitsi.jibri.capture.ffmpeg.OutputLineClassification
import org.jitsi.jibri.capture.ffmpeg.OutputParser

internal class OutputParserTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        context("An encoding output line") {
            val outputLine =
                "frame=   95 fps= 31 q=27.0 size=     584kB time=00:00:03.60 bitrate=1329.4kbits/s speed=1.19x"
//            val expectedValues = mapOf(
//                    "frame" to "95",
//                    "fps" to "31",
//                    "q" to "27.0",
//                    "size" to "584kB",
//                    "time" to "00:00:03.60",
//                    "bitrate" to "1329.4kbits/s",
//                    "speed" to "1.19x"
//            )

            should("be parsed correctly") {
                val status = OutputParser.parse(outputLine)
                status.lineType shouldBe OutputLineClassification.ENCODING
                status.detail shouldBe outputLine
            }
        }
        context("A warning output line") {
            val outputLine = "Past duration 0.622368 too large"
            should("be parsed correctly") {
                val status = OutputParser.parse(outputLine)
                status.lineType shouldBe OutputLineClassification.UNKNOWN
                status.detail.shouldBe(outputLine)
            }
        }
        context("An error output line") {
            context("with an error on the session scope") {
                val outputLine = "rtmp://a.rtmp.youtube.com/live2/dkafkjlafkjhsadf: Input/output error"
                should("be parsed correctly") {
                    val status = OutputParser.parse(outputLine)
                    status should beInstanceOf<FfmpegErrorStatus>()
                    status as FfmpegErrorStatus
                    status.detail.shouldBe(outputLine)
                    status.error.scope shouldBe ErrorScope.SESSION
                }
            }
        }
        context("A broken pipe output line") {
            val outputLine = "av_interleaved_write_frame(): Broken pipe"
            should("be parsed correctly") {
                val status = OutputParser.parse(outputLine)
                status should beInstanceOf<FfmpegErrorStatus>()
                status as FfmpegErrorStatus
                status.detail.shouldBe(outputLine)
                status.error.scope shouldBe ErrorScope.SESSION
            }
        }
        context("An unexpected exit output line") {
            val outputLine = "Exiting normally, received signal 15."
            should("be parsed correctly") {
                val status = OutputParser.parse(outputLine)
                status should beInstanceOf<FfmpegErrorStatus>()
                status as FfmpegErrorStatus
                status.detail.shouldBe(outputLine)
                status.error.scope shouldBe ErrorScope.SESSION
            }
        }
        context("An expected exit output line") {
            val outputLine = "Exiting normally, received signal 2."
            should("be parsed correctly") {
                val status = OutputParser.parse(outputLine)
                status.lineType shouldBe OutputLineClassification.FINISHED
                status.detail.shouldBe(outputLine)
            }
        }
        context("An unknonwn output line") {
            val outputLine = "some unknown ffmpeg status"
            should("be parsed correctly") {
                val status = OutputParser.parse(outputLine)
                status.lineType shouldBe OutputLineClassification.UNKNOWN
                status.detail.shouldBe(outputLine)
            }
        }
    }
}
