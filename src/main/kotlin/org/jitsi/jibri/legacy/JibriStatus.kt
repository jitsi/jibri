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

package org.jitsi.jibri.legacy

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import org.jitsi.jibri.EnvironmentContext

// The types in this file exist only for backwards compatibility, as they are what the
// previous Jibri version used and we need to provide a smooth transition for external
// services which may need to query old and new Jibris.

enum class ComponentBusyStatus {
    BUSY,
    IDLE,
    /**
     * This Jibri has exhausted its 'use' and needs action
     * (e.g. a restart) before it can be used again
     */
    EXPIRED
}

/**
 * A simple model of whether or not a component is healthy or not.  This enum is used to represent health at
 * multiple levels: sub-components within Jibri and Jibri's overall health.
 */
enum class ComponentHealthStatus {
    HEALTHY,
    UNHEALTHY;

    /**
     * Performs a logical 'and' of statuses, where:
     * HEALTHY.and(HEALTHY) -> HEALTHY
     * HEALTHY.and(UNHEALTHY) -> UNHEALTHY
     * UNHEALTHY.and(HEALTHY) -> UNHEALTHY
     * UNHEALTHY.and(UNHEALTHY) -> UNHEALTHY
     */
    fun and(other: ComponentHealthStatus): ComponentHealthStatus {
        if (this == HEALTHY && other == HEALTHY) {
            return HEALTHY
        }
        return UNHEALTHY
    }
}

/**
 * The combination of a [ComponentHealthStatus] and an optional detail string to elaborate on the status
 */
data class ComponentHealthDetails(
    val healthStatus: ComponentHealthStatus,
    val detail: String = ""
)

/**
 * The [ComponentHealthStatus] representing the overall health of the entire Jibri, as well as the
 * [ComponentHealthDetails] for each sub component
 */
data class OverallHealth(
    val healthStatus: ComponentHealthStatus,
    val details: Map<String, ComponentHealthDetails>
)

/**
 * The overall status of this Jibri.  This includes both its [ComponentBusyStatus] and its [OverallHealth]
 */
data class JibriStatus(
    val busyStatus: ComponentBusyStatus,
    val health: OverallHealth
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class JibriHealth(
    /**
     * Whether or not this Jibri is "busy". See [JibriManager#busy]
     */
    private val status: JibriStatus,
    /**
     * Context for the environment Jibri is currently active on
     * (only present if [busy] is true)
     */
    private val environmentContext: EnvironmentContext? = null
)
