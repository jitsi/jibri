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
package org.jitsi.jibri.status

import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.utils.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

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

/**
 * Models Jibri's overall status which currently consists of 2 things:
 * 1) Its 'busy' status: whether or not this Jibri is currently 'busy'
 * 2) Its health: whether or not this Jibri is capable of handling requests (regardless of
 * its busy status)
 */
class JibriStatusManager : StatusPublisher<JibriStatus>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val subComponentHealth: MutableMap<String, ComponentHealthDetails> = ConcurrentHashMap()
    /**
     * The overall [ComponentHealthStatus] for the entire Jibri, calculated by aggregating all sub-component
     * health status
     */
    private val overallHealthStatus: ComponentHealthStatus
        get() {
            return subComponentHealth.values.fold(ComponentHealthStatus.HEALTHY) { overallStatus, currDetails ->
                overallStatus.and(currDetails.healthStatus)
            }
        }

    /**
     * The overall health status plus any health details for each component
     */
    private val overallHealth: OverallHealth
        get() {
            return OverallHealth(
                overallHealthStatus,
                subComponentHealth.toMap()
            )
        }

    /**
     * The overall status contains both the health and busy statuses
     */
    val overallStatus: JibriStatus
        get() = JibriStatus(busyStatus, overallHealth)

    /**
     * The busy status for this Jibri
     */
    var busyStatus: ComponentBusyStatus by Delegates.observable(ComponentBusyStatus.IDLE) { _, old, new ->
        if (old != new) {
            logger.info("Busy status has changed: $old -> $new")
            publishStatus(overallStatus)
        }
    }

    /**
     * API for sub-components to update their health status
     */
    @Synchronized
    fun updateHealth(componentName: String, healthStatus: ComponentHealthStatus, detail: String = "") {
        logger.info(
            "Received component health update: $componentName has status $healthStatus " +
                "(detail: $detail)"
        )
        val oldHealthStatus = overallHealthStatus
        subComponentHealth[componentName] = ComponentHealthDetails(healthStatus, detail)
        val newHealthStatus = overallHealthStatus
        if (oldHealthStatus != newHealthStatus) {
            logger.info("Health status has changed: $oldHealthStatus -> $newHealthStatus")
            publishStatus(overallStatus)
        }
    }
}
