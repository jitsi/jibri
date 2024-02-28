/*
 * Copyright @ 2024-Present 8x8, Inc
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
package org.jitsi.jibri.metrics

import com.timgroup.statsd.NonBlockingStatsDClient
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.utils.logging2.createLogger

class JibriMetrics {
    private val logger = createLogger()

    private val statsd = if (StatsConfig.enableStatsD) {
        NonBlockingStatsDClient(
            "jibri",
            StatsConfig.statsdHost,
            StatsConfig.statsdPort
        )
    } else {
        null
    }

    private fun incrementStatsDCounter(aspect: String, vararg tags: String) {
        statsd?.let {
            logger.debug { "Incrementing statsd counter: $aspect:${tags.joinToString(":")}" }
            it.incrementCounter(aspect, *tags)
        }
    }

    fun busy(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_BUSY, type.getTag())
    }

    fun start(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_START, type.getTag())
    }

    fun stop(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_STOP, type.getTag())
    }

    fun error(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_ERROR, type.getTag())
    }

    fun xmppConnected(tags: String) {
        incrementStatsDCounter(XMPP_CONNECTED, tags)
    }

    fun xmppReconnecting(tags: String) {
        incrementStatsDCounter(XMPP_RECONNECTING, tags)
    }

    fun xmppReconnectionFailed(tags: String) {
        incrementStatsDCounter(XMPP_RECONNECTION_FAILED, tags)
    }

    fun xmppPingFailed(tags: String) {
        incrementStatsDCounter(XMPP_PING_FAILED, tags)
    }

    fun xmppClosed(tags: String) {
        incrementStatsDCounter(XMPP_CLOSED, tags)
    }

    fun xmppClosedOnError(tags: String) {
        incrementStatsDCounter(XMPP_CLOSED_ON_ERROR, tags)
    }

    fun stoppedOnXmppClosed(tags: String) {
        incrementStatsDCounter(STOPPED_ON_XMPP_CLOSED, tags)
    }
}

private fun RecordingSinkType.getTag() = when (this) {
    RecordingSinkType.STREAM -> TAG_SERVICE_LIVE_STREAM
    RecordingSinkType.FILE -> TAG_SERVICE_RECORDING
    RecordingSinkType.GATEWAY -> TAG_SERVICE_SIP_GATEWAY
}
