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
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatus
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

    private fun incrementStatsDCounter(aspect: String, tag: String) {
        statsd?.let {
            logger.debug { "Incrementing statsd counter: $aspect:$tag" }
            it.incrementCounter(aspect, tag)
        }
    }

    fun requestWhileBusy(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_BUSY, type.getTag())
        requestsWhileBusy.inc()
    }

    fun start(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_START, type.getTag())
        sessionsStarted.inc()
    }

    fun stop(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_STOP, type.getTag())
        sessionsStopped.inc()
    }

    fun error(type: RecordingSinkType) {
        incrementStatsDCounter(ASPECT_ERROR, type.getTag())
        errors.inc()
    }

    fun xmppConnected(tags: String) {
        incrementStatsDCounter(XMPP_CONNECTED, tags)
        xmppConnected.inc()
    }

    fun xmppReconnecting(tags: String) {
        incrementStatsDCounter(XMPP_RECONNECTING, tags)
        xmppReconnecting.inc()
    }

    fun xmppReconnectionFailed(tags: String) {
        incrementStatsDCounter(XMPP_RECONNECTION_FAILED, tags)
        xmppReconnectionFailed.inc()
    }

    fun xmppPingFailed(tags: String) {
        incrementStatsDCounter(XMPP_PING_FAILED, tags)
        xmppPingFailed.inc()
    }

    fun xmppClosed(tags: String) {
        incrementStatsDCounter(XMPP_CLOSED, tags)
        xmppClosed.inc()
    }

    fun xmppClosedOnError(tags: String) {
        incrementStatsDCounter(XMPP_CLOSED_ON_ERROR, tags)
        xmppClosedOnError.inc()
    }

    fun stoppedOnXmppClosed(tags: String) {
        incrementStatsDCounter(STOPPED_ON_XMPP_CLOSED, tags)
        stoppedOnXmppClosed.inc()
    }

    fun updateStatus(status: JibriStatus) {
        healthy.set(status.health.healthStatus == ComponentHealthStatus.HEALTHY)
        recording.set(status.busyStatus == ComponentBusyStatus.BUSY)
    }

    companion object {
        val sessionsStarted = JibriMetricsContainer.registerCounter(
            "sessions_started",
            "Number of times a session was started."
        )
        val sessionsStopped = JibriMetricsContainer.registerCounter(
            "sessions_stopped",
            "Number of times a session was stopped."
        )
        val errors = JibriMetricsContainer.registerCounter(
            "errors",
            "Number of errors."
        )
        val requestsWhileBusy = JibriMetricsContainer.registerCounter(
            "busy",
            "Number of times a request was received while the instance was busy."
        )
        val xmppConnected = JibriMetricsContainer.registerCounter(
            "xmpp_connected",
            "Number of times an XMPP connection connected."
        )
        val xmppReconnecting = JibriMetricsContainer.registerCounter(
            "xmpp_reconnecting",
            "Number of times an XMPP connection started re-connecting."
        )
        val xmppReconnectionFailed = JibriMetricsContainer.registerCounter(
            "xmpp_reconnection_failed",
            "Number of times an XMPP re-connection failed."
        )
        val xmppPingFailed = JibriMetricsContainer.registerCounter(
            "xmpp_ping_failed",
            "Number of times an XMPP ping timed out."
        )
        val xmppClosed = JibriMetricsContainer.registerCounter(
            "xmpp_closed",
            "Number of times an XMPP connection was closed."
        )
        val xmppClosedOnError = JibriMetricsContainer.registerCounter(
            "xmpp_closed_on_error",
            "Number of times an XMPP connection was closed on error."
        )
        val stoppedOnXmppClosed = JibriMetricsContainer.registerCounter(
            "stopped_on_xmpp_closed",
            "Number of times a session was stopped because XMPP disconnected."
        )
        val healthy = JibriMetricsContainer.registerBooleanMetric(
            "healthy",
            "Whether the jibri instance is currently healthy."
        )
        val recording = JibriMetricsContainer.registerBooleanMetric(
            "recording",
            "Whether the jibri instance is currently in use."
        )
    }
}

private fun RecordingSinkType.getTag() = when (this) {
    RecordingSinkType.STREAM -> TAG_SERVICE_LIVE_STREAM
    RecordingSinkType.FILE -> TAG_SERVICE_RECORDING
    RecordingSinkType.GATEWAY -> TAG_SERVICE_SIP_GATEWAY
}
