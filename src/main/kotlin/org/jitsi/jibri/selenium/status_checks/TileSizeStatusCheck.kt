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

import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.util.Resolution
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger

/**
 * Checks once that the client gave the tiles the resolution which the request asked for.
 *
 * We pick the screen canvas for a tile layout from a list which a person measured, because the client decides how it
 * lays tiles out. When the client changes that, the canvas stops giving the requested resolution, and nothing else
 * shows it. This check is the alarm for that: [onMismatch] should fire a metric.
 *
 * The size of a tile depends on how many tiles there are, so a size is only comparable when the client shows exactly
 * [tileCount] tiles. Participants usually arrive after the recorder joins, so we wait for that layout on every run of
 * the recurring checks, and compare once when it appears. We do not compare again later: one measurement per session
 * is enough to find a stale canvas, and a metric for every participant change would be noise.
 *
 * It never returns an event. A recording of the wrong size is still better than no recording, so a mismatch must not
 * stop the session.
 */
class TileSizeStatusCheck(
    parentLogger: Logger,
    private val tileResolution: Resolution,
    private val tileCount: Int,
    private val onMismatch: () -> Unit
) : CallStatusCheck {
    private val logger = createChildLogger(parentLogger)

    /** Whether we compared already, or found that we cannot. */
    private var done = false

    override fun run(callPage: CallPage): SeleniumEvent? {
        if (done) {
            return null
        }

        val layout = callPage.getTileLayout()
        if (layout == null) {
            logger.info("Cannot read the tile layout, not checking the tile size")
            done = true
            return null
        }
        if (layout.tileCount != tileCount) {
            // Not the layout we asked for yet, so its tile size says nothing about the canvas.
            return null
        }

        done = true
        if (layout.tileSize == tileResolution) {
            logger.info("The $tileCount tiles have the requested size, $tileResolution")
        } else {
            logger.error("Asked for $tileCount tiles of $tileResolution, the client made them ${layout.tileSize}")
            onMismatch()
        }
        return null
    }
}
