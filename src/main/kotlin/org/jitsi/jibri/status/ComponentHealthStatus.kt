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
