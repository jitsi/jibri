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

import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.statsd.ASPECT_BUSY
import org.jitsi.jibri.statsd.ASPECT_ERROR
import org.jitsi.jibri.statsd.ASPECT_START
import org.jitsi.jibri.statsd.ASPECT_STOP
import org.jitsi.jibri.statsd.JibriStatsDClient
import org.jitsi.jibri.statsd.STOPPED_ON_XMPP_CLOSED
import org.jitsi.jibri.statsd.TAG_SERVICE_LIVE_STREAM
import org.jitsi.jibri.statsd.TAG_SERVICE_RECORDING
import org.jitsi.jibri.statsd.TAG_SERVICE_SIP_GATEWAY
import org.jitsi.jibri.statsd.XMPP_CLOSED
import org.jitsi.jibri.statsd.XMPP_CLOSED_ON_ERROR
import org.jitsi.jibri.statsd.XMPP_CONNECTED
import org.jitsi.jibri.statsd.XMPP_PING_FAILED
import org.jitsi.jibri.statsd.XMPP_RECONNECTING
import org.jitsi.jibri.statsd.XMPP_RECONNECTION_FAILED

class JibriMetrics {
    private val statsDClient: JibriStatsDClient? = if (StatsConfig.enableStatsD) {
        JibriStatsDClient(StatsConfig.statsdHost, StatsConfig.statsdPort)
    } else {
        null
    }

    fun busy(type: RecordingSinkType) {
        statsDClient?.incrementCounter(ASPECT_BUSY, type.getTag())
    }

    fun start(type: RecordingSinkType) {
        statsDClient?.incrementCounter(ASPECT_START, type.getTag())
    }

    fun stop(type: RecordingSinkType) {
        statsDClient?.incrementCounter(ASPECT_STOP, type.getTag())
    }

    fun error(type: RecordingSinkType) {
        statsDClient?.incrementCounter(ASPECT_ERROR, type.getTag())
    }

    fun xmppConnected(tags: String) {
        statsDClient?.incrementCounter(XMPP_CONNECTED, tags)
    }

    fun xmppReconnecting(tags: String) {
        statsDClient?.incrementCounter(XMPP_RECONNECTING, tags)
    }

    fun xmppReconnectionFailed(tags: String) {
        statsDClient?.incrementCounter(XMPP_RECONNECTION_FAILED, tags)
    }

    fun xmppPingFailed(tags: String) {
        statsDClient?.incrementCounter(XMPP_PING_FAILED, tags)
    }

    fun xmppClosed(tags: String) {
        statsDClient?.incrementCounter(XMPP_CLOSED, tags)
    }

    fun xmppClosedOnError(tags: String) {
        statsDClient?.incrementCounter(XMPP_CLOSED_ON_ERROR, tags)
    }

    fun stoppedOnXmppClosed(tags: String) {
        statsDClient?.incrementCounter(STOPPED_ON_XMPP_CLOSED, tags)
    }
}

private fun RecordingSinkType.getTag() = when (this) {
    RecordingSinkType.STREAM -> TAG_SERVICE_LIVE_STREAM
    RecordingSinkType.FILE -> TAG_SERVICE_RECORDING
    RecordingSinkType.GATEWAY -> TAG_SERVICE_SIP_GATEWAY
}
