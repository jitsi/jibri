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

import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.service.impl.RtmpUrlValidator
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams

/**
 * Checks a start request before a session is created, and throws [BadRequestException] if the request can never
 * succeed. This is where a check for a new request parameter belongs.
 *
 * A parameter which is absent must always be valid. Jicofo builds the request it sends to us from a fixed list of
 * fields, so a release which does not know a parameter omits it. If we refused a request because a parameter is
 * missing, we would refuse every request from every such release.
 */
class StartRequestValidator(
    private val rtmpUrlValidator: RtmpUrlValidator = RtmpUrlValidator()
) {
    fun validate(params: StreamingParams) {
        rtmpUrlValidator.validate(params.rtmpUrl)
    }

    fun validate(params: FileRecordingRequestParams) {
        // No file recording parameter needs checking yet.
    }

    fun validate(params: SipGatewayServiceParams) {
        // No SIP gateway parameter needs checking yet.
    }
}
