/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
 *
 */
package org.jitsi.jibri.statsd

import com.timgroup.statsd.NonBlockingStatsDClient
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.impl.FileRecordingJibriService
import org.jitsi.jibri.service.impl.SipGatewayJibriService
import org.jitsi.jibri.service.impl.StreamingJibriService
import org.jitsi.jibri.util.extensions.debug
import java.util.logging.Logger

class StatsDClient {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val statsd = NonBlockingStatsDClient(
        "jibri",
        "localhost",
        8125
    )

    fun incrementCounter(aspect: String, vararg tags: String) {
        logger.debug("Incrementing statsd counter: $aspect:${tags.joinToString(":")}")
        statsd.incrementCounter(aspect, *tags)
    }

    companion object {
        fun getTagForService(service: JibriService?): String {
            return when (service) {
                is FileRecordingJibriService -> TAG_SERVICE_RECORDING
                is StreamingJibriService -> TAG_SERVICE_LIVE_STREAM
                is SipGatewayJibriService -> TAG_SERVICE_SIP_GATEWAY
                else -> ""
            }
        }
    }
}
