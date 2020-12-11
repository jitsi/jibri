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

package org.jitsi.jibri

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.jitsi.jibri.legacy.ComponentBusyStatus
import org.jitsi.jibri.legacy.ComponentHealthStatus
import org.jitsi.jibri.legacy.JibriHealth
import org.jitsi.jibri.legacy.JibriStatus
import org.jitsi.jibri.legacy.OverallHealth

@JsonSerialize(using = JibriStateSerializer::class)
sealed class JibriState {
    class Busy(val environmentContext: EnvironmentContext?) : JibriState() {
        override fun toString(): String = "Busy (${environmentContext ?: "no env context"})"
    }
    object Idle : JibriState() {
        override fun toString(): String = "Idle"
    }
    object Expired : JibriState() {
        override fun toString(): String = "Expired"
    }
    class Error(private val cause: JibriSystemError) : JibriState() {
        override fun toString(): String = "Error (${cause.message})"
    }
}

fun JibriState.toLegacyJibriStatus(): JibriStatus {
    val busyStatus = when (this) {
        is JibriState.Busy -> ComponentBusyStatus.BUSY
        is JibriState.Idle, is JibriState.Error -> ComponentBusyStatus.IDLE
        is JibriState.Expired -> ComponentBusyStatus.EXPIRED
    }

    val healthStatus = when (this) {
        is JibriState.Busy, JibriState.Idle, JibriState.Expired -> ComponentHealthStatus.HEALTHY
        is JibriState.Error -> ComponentHealthStatus.UNHEALTHY
    }
    return JibriStatus(busyStatus, OverallHealth(healthStatus, mapOf()))
}

fun JibriState.toLegacyJibriHealth(): JibriHealth {
    val status = toLegacyJibriStatus()
    val envContext = if (this is JibriState.Busy) {
        this.environmentContext
    } else null
    return JibriHealth(status, envContext)
}

/**
 * This and the custom serializer exist for backwards compatibility with the old-style.
 */
fun JibriState.statusString(): String {
    return when (this) {
        is JibriState.Busy -> "BUSY"
        is JibriState.Idle -> "IDLE"
        is JibriState.Expired -> "EXPIRED"
        is JibriState.Error -> "ERROR"
    }
}

class JibriStateSerializer : StdSerializer<JibriState>(JibriState::class.java) {
    override fun serialize(jibriState: JibriState, jgen: JsonGenerator, provider: SerializerProvider) {
        with(jgen) {
            writeStartObject()
            writeStringField("status", jibriState.statusString())
            if (jibriState is JibriState.Busy) {
                writeObjectField("environmentContext", jibriState.environmentContext)
            }
            writeEndObject()
        }
    }
}
