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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.util.Resolution

class RecordingProfilesTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val twoBy720p = TileCanvas(Resolution(1280, 720), 2, Resolution(2582, 748))
    val profiles = RecordingProfiles(listOf(twoBy720p), tileLayoutsEnabled = true)

    context("A request without recording parameters") {
        should("use the default recording") {
            profiles.resolve(null) shouldBe null
        }
        should("use the default recording when every parameter is absent") {
            profiles.resolve(RequestedRecordingParams()) shouldBe null
        }
    }

    context("A request for a supported tile layout") {
        val profile = profiles.resolve(RequestedRecordingParams("1280x720", "2", "3"))

        should("use the canvas which gives that layout") {
            profile shouldBe RecordingProfile(
                canvas = Resolution(2582, 748),
                tileResolution = Resolution(1280, 720),
                tileCount = 2,
                maxFullResolutionParticipants = 3
            )
        }
        should("put the client in tile view") {
            profile!!.useTileView shouldBe true
        }
        should("stop the client from cropping the video to fill a tile") {
            // Without this the tile is not 16:9, so the tile resolution we asked for is not what we record.
            profile!!.urlParams() shouldContain "config.disableTileEnlargement=true"
        }
        should("pass on maxFullResolutionParticipants") {
            profile!!.urlParams() shouldContain "config.maxFullResolutionParticipants=3"
        }
    }

    context("A request for a tile layout we have no canvas for") {
        should("be refused") {
            shouldThrow<BadRequestException> { profiles.resolve(RequestedRecordingParams("1280x720", "3")) }
            shouldThrow<BadRequestException> { profiles.resolve(RequestedRecordingParams("1920x1080", "2")) }
        }
    }

    context("A request with only one of the two tile parameters") {
        should("be refused, because neither is usable on its own") {
            shouldThrow<BadRequestException> { profiles.resolve(RequestedRecordingParams(tileResolution = "1280x720")) }
            shouldThrow<BadRequestException> { profiles.resolve(RequestedRecordingParams(tileCount = "2")) }
        }
    }

    context("A request with a malformed value") {
        should("be refused rather than recorded with a default") {
            shouldThrow<BadRequestException> { profiles.resolve(RequestedRecordingParams("720p", "2")) }
            shouldThrow<BadRequestException> { profiles.resolve(RequestedRecordingParams("1280x720", "two")) }
            shouldThrow<BadRequestException> {
                profiles.resolve(RequestedRecordingParams(maxFullResolutionParticipants = "three"))
            }
        }
    }

    context("A request for maxFullResolutionParticipants outside the range we accept") {
        should("be refused") {
            shouldThrow<BadRequestException> {
                profiles.resolve(RequestedRecordingParams(maxFullResolutionParticipants = "-2"))
            }
            shouldThrow<BadRequestException> {
                profiles.resolve(RequestedRecordingParams(maxFullResolutionParticipants = "26"))
            }
        }
        should("accept -1, which turns the limit off") {
            profiles.resolve(RequestedRecordingParams(maxFullResolutionParticipants = "-1"))
                ?.maxFullResolutionParticipants shouldBe -1
        }
    }

    context("A request for maxFullResolutionParticipants alone") {
        val profile = profiles.resolve(RequestedRecordingParams(maxFullResolutionParticipants = "3"))

        should("keep the default screen resolution") {
            profile!!.canvas shouldBe null
        }
        should("keep the default layout") {
            profile!!.useTileView shouldBe false
            profile.urlParams() shouldNotContain "config.disableTileEnlargement=true"
        }
    }

    context("When tile layouts are disabled") {
        val disabled = RecordingProfiles(listOf(twoBy720p), tileLayoutsEnabled = false)

        should("refuse a tile layout, because we cannot set the canvas") {
            shouldThrow<BadRequestException> { disabled.resolve(RequestedRecordingParams("1280x720", "2")) }
        }
        should("still accept maxFullResolutionParticipants, which does not need the canvas") {
            disabled.resolve(RequestedRecordingParams(maxFullResolutionParticipants = "3"))
                ?.maxFullResolutionParticipants shouldBe 3
        }
    }
})
