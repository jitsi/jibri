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

import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.selenium.status_checks.TileSizeStatusCheck
import org.jitsi.jibri.util.Resolution
import org.jitsi.utils.logging2.Logger

/**
 * The recording parameters as they arrive in a start request. They say what the recording must look like. They say
 * nothing about how we make it.
 *
 * Nothing is checked here. A value can be absent, or ask for something we cannot do. [RecordingProfiles.resolve]
 * turns this into the [RecordingProfile] we use, or refuses the request.
 */
data class RequestedRecordingParams(
    /** The resolution of one tile, in the "WIDTHxHEIGHT" format. */
    val tileResolution: String? = null,
    /** How many tiles the recording must show. */
    val tileCount: String? = null,
    /** The value to use for the maxFullResolutionParticipants option of the client. */
    val maxFullResolutionParticipants: String? = null
) {
    val isEmpty: Boolean
        get() = tileResolution == null && tileCount == null && maxFullResolutionParticipants == null
}

/**
 * What we do for a request, after we accepted it. A field which is null keeps the default behavior.
 */
data class RecordingProfile(
    /** The screen resolution to set before Chrome starts, or null to keep the default resolution. */
    val canvas: Resolution? = null,
    /** The resolution each tile must have. We check the achieved size against it once the call is joined. */
    val tileResolution: Resolution? = null,
    val tileCount: Int? = null,
    val maxFullResolutionParticipants: Int? = null
) {
    /** Whether we must put the client in tile view. */
    val useTileView: Boolean
        get() = tileCount != null

    /** The client options which give this profile. */
    fun urlParams(): List<String> = buildList {
        if (useTileView) {
            // A tile which is not 16:9 crops the video to fill it, so the requested tile resolution would not be
            // what we record. This is not optional, and it is therefore not a request parameter.
            add("config.disableTileEnlargement=true")
        }
        maxFullResolutionParticipants?.let { add("config.maxFullResolutionParticipants=$it") }
    }

    /**
     * The check that the client gave the tiles the requested resolution, or null if this profile has no tile layout.
     * It runs with the recurring call checks, because it must wait until the client shows the requested number of
     * tiles.
     */
    fun createTileSizeCheck(parentLogger: Logger, onMismatch: () -> Unit): TileSizeStatusCheck? {
        val tileResolution = tileResolution ?: return null
        val tileCount = tileCount ?: return null
        return TileSizeStatusCheck(parentLogger, tileResolution, tileCount, onMismatch)
    }
}

/**
 * A canvas which gives [tileCount] tiles of [tileResolution]. Both the canvas and the tile layout are decided by the
 * client, so we cannot compute this: we look it up in a list which a person verified.
 */
data class TileCanvas(val tileResolution: Resolution, val tileCount: Int, val canvas: Resolution)

/**
 * Decides what we do for a set of requested recording parameters, and refuses a request which we cannot serve.
 *
 * We refuse rather than record something else, because a recording which silently has the wrong size is worse than
 * no recording: nobody sees the problem until the recording is needed.
 */
class RecordingProfiles(
    private val tileCanvases: List<TileCanvas> = ScreenConfig.tileCanvases,
    /** Whether we can change the screen resolution. A tile layout needs a canvas, so without this we refuse it. */
    private val tileLayoutsEnabled: Boolean = ScreenConfig.xrandrEnabled
) {
    fun resolve(requested: RequestedRecordingParams?): RecordingProfile? {
        if (requested == null || requested.isEmpty) {
            return null
        }

        val maxFullResolutionParticipants =
            requested.maxFullResolutionParticipants?.toIntOrBadRequest("maxFullResolutionParticipants")?.also {
                if (it !in MAX_FULL_RESOLUTION_PARTICIPANTS_RANGE) {
                    throw BadRequestException(
                        "maxFullResolutionParticipants must be in $MAX_FULL_RESOLUTION_PARTICIPANTS_RANGE, got $it"
                    )
                }
            }

        if (requested.tileResolution == null && requested.tileCount == null) {
            return RecordingProfile(maxFullResolutionParticipants = maxFullResolutionParticipants)
        }
        if (requested.tileResolution == null || requested.tileCount == null) {
            throw BadRequestException("tileResolution and tileCount must be requested together")
        }
        if (!tileLayoutsEnabled) {
            throw BadRequestException("This Jibri does not support tile layouts, because xrandr is disabled")
        }

        val tileCount = requested.tileCount.toIntOrBadRequest("tileCount")
        val tileResolution = Resolution.parse(requested.tileResolution)
            ?: throw BadRequestException(
                "tileResolution must have the WIDTHxHEIGHT format, got '${requested.tileResolution}'"
            )
        val canvas = tileCanvases.find { it.tileResolution == tileResolution && it.tileCount == tileCount }
            ?: throw BadRequestException(
                "No canvas for $tileCount tiles of $tileResolution. Supported: " +
                    tileCanvases.joinToString { "${it.tileCount} tiles of ${it.tileResolution}" }
            )

        return RecordingProfile(
            canvas = canvas.canvas,
            tileResolution = tileResolution,
            tileCount = tileCount,
            maxFullResolutionParticipants = maxFullResolutionParticipants
        )
    }

    companion object {
        /**
         * The range we accept for maxFullResolutionParticipants. -1 turns the limit off in the client, and the upper
         * bound keeps a request from asking for more high resolution streams than a Jibri can decode.
         */
        private val MAX_FULL_RESOLUTION_PARTICIPANTS_RANGE = -1..25
    }
}

/**
 * Reads a number which arrived in a request. A value which is not a number must not read as "not requested": we
 * would then record with our own default, and nobody would know that the request was not served.
 */
private fun String.toIntOrBadRequest(what: String) = toIntOrNull()
    ?: throw BadRequestException("$what must be a number, got '$this'")
