/*
 * Copyright @ 2026 - present 8x8, Inc.
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
package org.jitsi.jibri.util

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jitsi.utils.logging2.LoggerImpl

/** The output of "xrandr --current" on a Jibri, shortened. */
private fun xrandrOutput(width: Int, height: Int) = """
    Screen 0: minimum 8 x 8, current $width x $height, maximum 32767 x 32767
    DUMMY0 connected primary ${width}x$height+0+0 (normal left inverted right x axis y axis) 0mm x 0mm
       1920x1080     10.00 +
       2582x748      10.00
""".trimIndent()

class XrandrTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val logger = LoggerImpl("XrandrTest")
    val commands = mutableListOf<List<String>>()
    var screen = Resolution(1920, 1080)

    /** Answers a query with the current screen size, and applies a --mode to it. */
    val fakeXrandr: (List<String>) -> String = { command ->
        commands.add(command)
        val modeIndex = command.indexOf("--mode")
        if (modeIndex >= 0) {
            screen = Resolution.parse(command[modeIndex + 1])!!
        }
        xrandrOutput(screen.width, screen.height)
    }

    context("Reading the screen") {
        val xrandr = Xrandr(logger, runCommand = fakeXrandr)

        should("report the current resolution") {
            xrandr.currentResolution() shouldBe Resolution(1920, 1080)
        }
        should("find the connected output") {
            xrandr.connectedOutput() shouldBe "DUMMY0"
        }
        should("report nothing when xrandr fails") {
            Xrandr(logger, runCommand = { throw XrandrException("no xrandr") }).currentResolution() shouldBe null
        }
    }

    context("Setting the resolution") {
        val xrandr = Xrandr(logger, runCommand = fakeXrandr)

        should("ask xrandr for the mode on the connected output") {
            xrandr.setResolution(Resolution(2582, 748))

            commands.single { it.contains("--mode") } shouldContainAll
                listOf("--output", "DUMMY0", "--mode", "2582x748")
            xrandr.currentResolution() shouldBe Resolution(2582, 748)
        }
        should("do nothing when the screen already has that resolution") {
            xrandr.setResolution(Resolution(1920, 1080))

            // Only the query, no change.
            commands shouldHaveSize 1
        }
    }

    context("Setting a resolution which the X server cannot give us") {
        // The X server keeps its size when it has no Modeline for the mode, or when the mode is missing from the
        // "Modes" of its Screen section.
        val xrandr = Xrandr(
            logger,
            runCommand = { command ->
                commands.add(command)
                xrandrOutput(1920, 1080)
            }
        )

        should("fail rather than record at the wrong size") {
            shouldThrow<XrandrException> { xrandr.setResolution(Resolution(2582, 748)) }
        }
        should("say what to do about it") {
            val e = shouldThrow<XrandrException> { xrandr.setResolution(Resolution(2582, 748)) }
            e.message!!.contains("Modeline") shouldBe true
        }
        should("accept the resolution the screen already has") {
            shouldNotThrowAny { xrandr.setResolution(Resolution(1920, 1080)) }
        }
    }
})
