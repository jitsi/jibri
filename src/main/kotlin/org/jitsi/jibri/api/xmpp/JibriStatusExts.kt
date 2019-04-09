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
package org.jitsi.jibri.api.xmpp

import org.jitsi.xmpp.extensions.health.HealthStatusPacketExt
import org.jitsi.xmpp.extensions.jibri.JibriBusyStatusPacketExt
import org.jitsi.xmpp.extensions.jibri.JibriStatusPacketExt
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatus

/**
 * Translate the Jibri busy status enum to the jitsi-protocol-jabber version
 */
private fun ComponentBusyStatus.toBusyStatusExt(): JibriBusyStatusPacketExt.BusyStatus {
    return when (this) {
        ComponentBusyStatus.BUSY -> JibriBusyStatusPacketExt.BusyStatus.BUSY
        ComponentBusyStatus.IDLE -> JibriBusyStatusPacketExt.BusyStatus.IDLE
    }
}

/**
 * Translate the Jibri health status enum to the jitsi-protocol-jabber version
 */
private fun ComponentHealthStatus.toHealthStatusExt(): HealthStatusPacketExt.Health {
    return when (this) {
        ComponentHealthStatus.HEALTHY -> HealthStatusPacketExt.Health.HEALTHY
        ComponentHealthStatus.UNHEALTHY -> HealthStatusPacketExt.Health.UNHEALTHY
    }
}

/**
 * Convert a [JibriStatus] to [JibriStatusPacketExt]
 */
fun JibriStatus.toJibriStatusExt(): JibriStatusPacketExt {
    val jibriStatusExt = JibriStatusPacketExt()

    val jibriBusyStatusExt = JibriBusyStatusPacketExt()
    jibriBusyStatusExt.status = this.busyStatus.toBusyStatusExt()
    jibriStatusExt.busyStatus = jibriBusyStatusExt

    val jibriHealthStatusExt = HealthStatusPacketExt()
    jibriHealthStatusExt.status = this.health.healthStatus.toHealthStatusExt()
    jibriStatusExt.healthStatus = jibriHealthStatusExt

    return jibriStatusExt
}
