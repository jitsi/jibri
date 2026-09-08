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
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.sipgateway.SipClientParams

class StartRequestValidatorTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val validator = StartRequestValidator()
    val callParams = CallParams(CallUrlInfo("http://example.com", "room"))
    val login = XmppCredentials(domain = "domain", username = "username", password = "password")

    /**
     * This is the regression test for compatibility with a Jicofo release which does not know a parameter. Such a
     * release omits it, so every request from it arrives without it. If a missing parameter were treated as invalid,
     * we would refuse every request from every such release the moment this Jibri is deployed.
     */
    context("A request which sets only the parameters that every jicofo release sends") {
        should("be valid for a stream") {
            shouldNotThrowAny {
                validator.validate(
                    StreamingParams(
                        callParams = callParams,
                        sessionId = "session",
                        callLoginParams = login,
                        rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/abcd-1234-efgh-5678-ijkl"
                    )
                )
            }
        }
        should("be valid for a file recording") {
            shouldNotThrowAny {
                validator.validate(FileRecordingRequestParams(callParams, "session", login))
            }
        }
        should("be valid for a SIP gateway call") {
            shouldNotThrowAny {
                validator.validate(
                    SipGatewayServiceParams(callParams, login, SipClientParams("sip:a@example.com", "display"))
                )
            }
        }
    }

    context("A stream with an invalid RTMP URL") {
        should("be refused") {
            shouldThrow<BadRequestException> {
                validator.validate(
                    StreamingParams(
                        callParams = callParams,
                        sessionId = "session",
                        callLoginParams = login,
                        rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/oooo"
                    )
                )
            }
        }
    }
})
