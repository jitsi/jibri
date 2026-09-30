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

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.util.Resolution
import org.jitsi.jibri.util.Xrandr
import org.jitsi.jibri.util.XrandrException
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger

/**
 * Controls the resolution of the X screen that Chrome draws on and that ffmpeg captures.
 *
 * When [xrandrEnabled] is true, we set the resolution before every session, and not only for a session which asks for
 * a tile layout. The X server outlives every session, so a session which ended badly (for example because Jibri
 * crashed) can leave the screen at another resolution. A default session which started on that screen would then
 * capture the wrong area, or fail.
 *
 * When [xrandrEnabled] is false, we never touch the screen. [RecordingProfiles] then refuses a tile layout, so a session
 * never asks for a canvas.
 */
class Screen(
    parentLogger: Logger,
    private val xrandr: Xrandr = Xrandr(parentLogger),
    val xrandrEnabled: Boolean = ScreenConfig.xrandrEnabled,
    private val defaultResolution: Resolution = ScreenConfig.defaultResolution
) {
    private val logger = createChildLogger(parentLogger)

    /**
     * Gives the screen the resolution for a session. Call it before Chrome starts, because Chrome reads the screen
     * size when it starts.
     *
     * @param canvas the canvas of the requested tile layout, or null for the default resolution.
     * @return the resolution of the screen, for ffmpeg to capture, or null if we do not control the screen. With
     * null, ffmpeg uses the resolution in its own config.
     * @throws XrandrException if the screen does not have the resolution afterwards.
     */
    fun prepare(canvas: Resolution?): Resolution? {
        if (!xrandrEnabled) {
            check(canvas == null) { "Cannot use the canvas $canvas, because xrandr is disabled" }
            return null
        }
        val resolution = canvas ?: defaultResolution
        xrandr.setResolution(resolution)
        return resolution
    }

    /**
     * Puts the default resolution back after a session. A failure here must not fail the session which just ended,
     * so we only log it. The next session sets the resolution again anyway.
     */
    fun restore() {
        if (!xrandrEnabled) {
            return
        }
        try {
            xrandr.setResolution(defaultResolution)
        } catch (t: Throwable) {
            logger.error("Failed to restore the screen resolution", t)
        }
    }
}

object ScreenConfig {
    val xrandrEnabled: Boolean by config {
        "jibri.screen.xrandr-enabled".from(Config.configSource)
    }

    val defaultResolution: Resolution by config {
        "jibri.screen.default-resolution".from(Config.configSource)
            .convertFrom<String> { it.toResolution("jibri.screen.default-resolution") }
    }

    /**
     * The canvas to use for each tile layout we support. A canvas only works if the X server also has a matching
     * Modeline and lists it in its Screen "Modes", because the X server fixes the virtual screen size when it
     * starts. Keep the two in step.
     */
    val tileCanvases: List<TileCanvas> by config {
        "jibri.screen.tile-canvases".from(Config.configSource)
            .convertFrom<List<com.typesafe.config.Config>> { canvases -> canvases.map { it.toTileCanvas() } }
    }
}

private fun com.typesafe.config.Config.toTileCanvas() = TileCanvas(
    tileResolution = getString("tile-resolution").toResolution("tile-resolution"),
    tileCount = getInt("tile-count"),
    canvas = getString("canvas").toResolution("canvas")
)

private fun String.toResolution(what: String) = Resolution.parse(this)
    ?: throw IllegalArgumentException("$what must have the WIDTHxHEIGHT format, got '$this'")
