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

package org.jitsi.jibri.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.StatusPublisher

enum class JibriServiceStatus {
    FINISHED,
    ERROR
}

/**
 * Arbitrary Jibri specific data that can be passed in the
 * [JibriIq#appData] field.  This entire structure will be parsed
 * from a JSON-encoded string (the [JibriIq#appData] field).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppData(
    /**
     * A JSON map representing arbitrary data to be written
     * to the metadata file when doing a recording.
     */
    @JsonProperty("file_recording_metadata")
    val fileRecordingMetadata: Map<Any, Any>?
)

/**
 * Parameters needed for starting any [JibriService]
 */
data class ServiceParams(
    val usageTimeoutMinutes: Int,
    val appData: AppData? = null
)

typealias JibriServiceStatusHandler = (ComponentState) -> Unit

/**
 * Interface implemented by all implemented [JibriService]s.  A [JibriService]
 * is responsible for an entire feature of Jibri, such as recording a call
 * to a file or streaming a call to a url.
 */
abstract class JibriService : StatusPublisher<ComponentState>() {
    /**
     * Starts this [JibriService]
     */
    abstract fun start()

    /**
     * Stops this [JibriService]
     */
    abstract fun stop()
}
