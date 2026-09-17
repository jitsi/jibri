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
package org.jitsi.jibri.service

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.util.Resolution
import org.jitsi.jibri.util.Xrandr
import org.jitsi.jibri.util.XrandrException
import org.jitsi.utils.logging2.Logger

class ScreenTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val logger: Logger = mockk(relaxed = true)
    val xrandr: Xrandr = mockk(relaxed = true)
    val defaultResolution = Resolution(1920, 1080)
    val canvas = Resolution(2582, 748)

    context("When xrandr is enabled") {
        val screen = Screen(logger, xrandr, xrandrEnabled = true, defaultResolution = defaultResolution)

        context("a session without a canvas") {
            val captured = screen.prepare(null)

            should("set the default resolution, because an earlier session can have left another one") {
                verify { xrandr.setResolution(defaultResolution) }
            }
            should("capture the default resolution") {
                captured shouldBe defaultResolution
            }
        }
        context("a session with a canvas") {
            val captured = screen.prepare(canvas)

            should("set the canvas") {
                verify { xrandr.setResolution(canvas) }
            }
            should("capture the canvas") {
                captured shouldBe canvas
            }
        }
        context("a failure to set the resolution") {
            every { xrandr.setResolution(any()) } throws XrandrException("no such mode")

            should("fail the session") {
                shouldThrow<XrandrException> { screen.prepare(null) }
            }
            should("not fail the restore") {
                shouldNotThrowAny { screen.restore() }
            }
        }
        context("restoring the screen") {
            screen.restore()

            should("set the default resolution") {
                verify { xrandr.setResolution(defaultResolution) }
            }
        }
    }

    context("When xrandr is disabled") {
        val screen = Screen(logger, xrandr, xrandrEnabled = false, defaultResolution = defaultResolution)

        should("not touch the screen, and let ffmpeg use its own config") {
            screen.prepare(null) shouldBe null
            screen.restore()
            verify(exactly = 0) { xrandr.setResolution(any()) }
        }
        should("not accept a canvas, which the request validation must have refused") {
            shouldThrow<IllegalStateException> { screen.prepare(canvas) }
        }
    }
})
