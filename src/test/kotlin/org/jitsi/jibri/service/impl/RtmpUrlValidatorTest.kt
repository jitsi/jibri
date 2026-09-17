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
package org.jitsi.jibri.service.impl

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.jitsi.jibri.error.BadRequestException
import java.util.regex.Pattern

private const val VALID_KEY = "abcd-1234-efgh-5678-ijkl"

class RtmpUrlValidatorTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val defaultYouTubeKeyPattern: Pattern = Pattern.compile("^[a-zA-Z0-9]{4}(-[a-zA-Z0-9]{4}){3,}$")
    val allowAll = listOf(Pattern.compile(".*"))
    val validator = RtmpUrlValidator(allowAll, defaultYouTubeKeyPattern)

    context("A valid YouTube URL") {
        should("be accepted") {
            shouldNotThrowAny { validator.validate("$YOUTUBE_URL/$VALID_KEY") }
        }
        should("be accepted with a four group key") {
            shouldNotThrowAny { validator.validate("$YOUTUBE_URL/abcd-1234-efgh-5678") }
        }
    }

    context("A YouTube URL with a malformed stream key") {
        // These are the keys from the incident that caused this check to be added.
        listOf("K", "o", "oooo", "B", "Boo", "U", "x", "abcd-1234-efgh", "abcd_1234_efgh_5678").forEach { key ->
            should("be rejected: '$key'") {
                shouldThrow<BadRequestException> { validator.validate("$YOUTUBE_URL/$key") }
            }
        }
        should("not put the stream key in the message") {
            val e = shouldThrow<BadRequestException> { validator.validate("$YOUTUBE_URL/secretkey") }
            e.message shouldNotBe null
            e.message!! shouldNotContain "secretkey"
        }
    }

    context("A non-YouTube URL") {
        should("not have the YouTube key pattern applied") {
            shouldNotThrowAny { validator.validate("rtmp://example.com/live/K") }
        }
        should("be accepted with rtmps") {
            shouldNotThrowAny { validator.validate("rtmps://example.com/live/K") }
        }
        should("be accepted with a single path segment, since not every server uses an app name") {
            shouldNotThrowAny { validator.validate("rtmp://myserver.local/mystreamkey") }
        }
        should("be accepted with an underscore in the host") {
            // java.net.URI.getHost() returns null for a host containing an underscore, a legal (if discouraged)
            // hostname character. getAuthority() still has it.
            shouldNotThrowAny { validator.validate("rtmp://my_server/live/$VALID_KEY") }
        }
    }

    context("A YouTube URL in a form other than the one Jibri builds itself") {
        // These must all still be caught: the pathological key "oooo" is from the incident, and a client can send
        // any of these forms directly, unlike Jicofo, which only ever asks for the exact form JIBRI builds.
        listOf(
            "rtmps://a.rtmp.youtube.com/live2/oooo",
            "rtmp://a.rtmp.youtube.com:1935/live2/oooo",
            "RTMP://a.rtmp.youtube.com/live2/oooo",
            "rtmp://b.rtmp.youtube.com/live2/oooo"
        ).forEach { url ->
            should("still catch a malformed key: '$url'") {
                shouldThrow<BadRequestException> { validator.validate(url) }
            }
        }
    }

    context("A structurally invalid URL") {
        should("be rejected when the scheme is not rtmp") {
            shouldThrow<BadRequestException> { validator.validate("https://example.com/live/$VALID_KEY") }
        }
        should("be rejected when there is no scheme") {
            shouldThrow<BadRequestException> { validator.validate("example.com/live/$VALID_KEY") }
        }
        should("be rejected when there is no host") {
            shouldThrow<BadRequestException> { validator.validate("rtmp:///live/$VALID_KEY") }
        }
        should("be rejected when there is no stream key") {
            shouldThrow<BadRequestException> { validator.validate("rtmp://example.com/") }
        }
        should("be rejected when the path is empty") {
            shouldThrow<BadRequestException> { validator.validate("rtmp://example.com") }
        }
        should("be rejected when the URI does not parse") {
            shouldThrow<BadRequestException> { validator.validate("rtmp://exa mple.com/live/$VALID_KEY") }
        }
    }

    context("The allow list") {
        val restricted = RtmpUrlValidator(
            listOf(Pattern.compile("rtmp://a\\.rtmp\\.youtube\\.com/.*")),
            defaultYouTubeKeyPattern
        )
        should("accept a URL that matches") {
            shouldNotThrowAny { restricted.validate("$YOUTUBE_URL/$VALID_KEY") }
        }
        should("reject a URL that does not match") {
            shouldThrow<BadRequestException> { restricted.validate("rtmp://example.com/live/$VALID_KEY") }
        }
    }

    context("A permissive stream key pattern") {
        val permissive = RtmpUrlValidator(allowAll, Pattern.compile(".*"))
        should("accept any YouTube stream key") {
            shouldNotThrowAny { permissive.validate("$YOUTUBE_URL/K") }
        }
    }
})
