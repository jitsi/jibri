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

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.metaconfig.config
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * Validates an RTMP URL before a streaming session is started, so that a request which can never succeed is rejected
 * while the Jibri is still idle. This keeps a client that repeats a bad request from consuming Jibri instances.
 */
class RtmpUrlValidator(
    private val allowList: List<Pattern> = configAllowList,
    private val youTubeStreamKeyPattern: Pattern = configYouTubeStreamKeyPattern
) {
    /**
     * Throws [InvalidRtmpUrlException] if [rtmpUrl] can not be used for a streaming session.
     */
    fun validate(rtmpUrl: String) {
        val uri = try {
            URI(rtmpUrl)
        } catch (e: URISyntaxException) {
            throw BadRequestException("The RTMP URL is not a valid URI: ${e.reason}")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "rtmp" && scheme != "rtmps") {
            throw BadRequestException("The RTMP URL scheme must be rtmp or rtmps, but it is $scheme")
        }
        if (uri.host.isNullOrBlank()) {
            throw BadRequestException("The RTMP URL has no host")
        }

        // The path must contain an application (e.g. "live2") and a stream key.
        val pathSegments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (pathSegments.size < 2) {
            throw BadRequestException("The RTMP URL has no stream key")
        }

        if (allowList.none { it.matcher(rtmpUrl).matches() }) {
            throw BadRequestException("The RTMP URL is not allowed")
        }

        // Only applied to the YouTube ingest URL, because only then do we know which format the stream key must have.
        if (rtmpUrl.startsWith("$YOUTUBE_URL/") &&
            !youTubeStreamKeyPattern.matcher(pathSegments.last()).matches()
        ) {
            throw BadRequestException("The YouTube stream key has an invalid format")
        }
    }

    companion object {
        val configAllowList: List<Pattern> by config {
            "jibri.streaming.rtmp-allow-list".from(Config.configSource)
                .convertFrom<List<String>> { it.map(Pattern::compile) }
        }

        val configYouTubeStreamKeyPattern: Pattern by config {
            "jibri.streaming.youtube-stream-key-pattern".from(Config.configSource)
                .convertFrom<String> { Pattern.compile(it) }
        }
    }
}
