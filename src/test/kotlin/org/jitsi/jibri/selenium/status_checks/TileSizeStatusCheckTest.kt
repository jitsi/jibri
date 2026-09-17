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
package org.jitsi.jibri.selenium.status_checks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.TileLayout
import org.jitsi.jibri.util.Resolution
import org.jitsi.utils.logging2.Logger

internal class TileSizeStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val callPage: CallPage = mockk()
    private val logger: Logger = mockk(relaxed = true)
    private var mismatches = 0

    private val requested = Resolution(1280, 720)
    private val check = TileSizeStatusCheck(logger, requested, 2) { mismatches++ }

    init {
        context("when fewer tiles are shown than requested") {
            // One participant: the single tile is limited by the height of the canvas, so it is bigger.
            every { callPage.getTileLayout() } returns TileLayout(1, Resolution(1298, 730))

            should("not report a mismatch") {
                repeat(5) { check.run(callPage) }
                mismatches shouldBe 0
            }
            should("keep checking until the requested layout appears") {
                check.run(callPage)
                every { callPage.getTileLayout() } returns TileLayout(2, Resolution(1276, 718))
                check.run(callPage)
                mismatches shouldBe 1
            }
        }
        context("when more tiles are shown than requested") {
            every { callPage.getTileLayout() } returns TileLayout(3, Resolution(851, 479))

            should("not report a mismatch") {
                check.run(callPage)
                mismatches shouldBe 0
            }
        }
        context("when the requested layout has the requested tile size") {
            every { callPage.getTileLayout() } returns TileLayout(2, requested)

            should("not report a mismatch") {
                check.run(callPage)
                mismatches shouldBe 0
            }
            should("stop checking") {
                check.run(callPage)
                check.run(callPage)
                verify(exactly = 1) { callPage.getTileLayout() }
            }
        }
        context("when the requested layout has another tile size") {
            every { callPage.getTileLayout() } returns TileLayout(2, Resolution(1276, 718))

            should("report a mismatch once") {
                repeat(5) { check.run(callPage) }
                mismatches shouldBe 1
            }
        }
        context("when the tile layout cannot be read") {
            every { callPage.getTileLayout() } returns null

            should("not report a mismatch, and stop trying") {
                check.run(callPage)
                check.run(callPage)
                mismatches shouldBe 0
                verify(exactly = 1) { callPage.getTileLayout() }
            }
        }
        context("in every case") {
            every { callPage.getTileLayout() } returns TileLayout(2, Resolution(1276, 718))

            should("never end the session, because a recording of the wrong size is better than none") {
                check.run(callPage) shouldBe null
                check.run(callPage) shouldBe null
            }
        }
    }
}
