/*
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jibri.statsd

import com.timgroup.statsd.NonBlockingStatsDClient
import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.recording.RecordingJob
import org.jitsi.jibri.job.sipgateway.SipGatewayJob
import org.jitsi.jibri.job.streaming.StreamingJob
import org.jitsi.utils.logging2.createLogger

/**
 * Client for pushing statsd values
 */
class JibriStatsDClient(hostname: String = "localhost", port: Int = 8125) {
    private val logger = createLogger()
    private val statsd = NonBlockingStatsDClient(
        "jibri",
        hostname,
        port
    )

    fun incrementCounter(aspect: String, vararg tags: String) {
        logger.debug("Incrementing statsd counter: $aspect:${tags.joinToString(":")}")
        statsd.incrementCounter(aspect, *tags)
    }
}

private fun getTagForJob(service: JibriJob?): String {
    return when (service) {
        is RecordingJob -> TAG_SERVICE_RECORDING
        is StreamingJob -> TAG_SERVICE_LIVE_STREAM
        is SipGatewayJob -> TAG_SERVICE_SIP_GATEWAY
        else -> ""
    }
}

fun JibriStatsDClient.requestDeniedBecauseBusy() = incrementCounter(ASPECT_BUSY, TAG_SERVICE_RECORDING)
fun JibriStatsDClient.recordingStarted() = incrementCounter(ASPECT_START, TAG_SERVICE_RECORDING)
fun JibriStatsDClient.liveStreamStarted() = incrementCounter(ASPECT_START, TAG_SERVICE_LIVE_STREAM)
fun JibriStatsDClient.sipGwStarted() = incrementCounter(ASPECT_START, TAG_SERVICE_SIP_GATEWAY)
fun JibriStatsDClient.jobHadError(job: JibriJob) {
    incrementCounter(ASPECT_ERROR, getTagForJob(job))
}
