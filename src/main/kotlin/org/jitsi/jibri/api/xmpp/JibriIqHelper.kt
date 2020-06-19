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

import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.xmpp.extensions.jibri.JibriStatusPacketExt
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Presence
import org.jxmpp.jid.Jid

// TODO: this functionality should be added to JibriIq

/**
 * Create a result iq from the given [JibriIq]
 */
class JibriIqHelper {
    companion object {
        fun create(from: Jid, type: IQ.Type = IQ.Type.set, status: JibriIq.Status = JibriIq.Status.UNDEFINED): JibriIq {
            val jibriIq = JibriIq()
            jibriIq.to = from
            jibriIq.type = type
            jibriIq.status = status

            return jibriIq
        }
    }
}

/**
 * Return a result IQ for this [JibriIq], setting a few fields and then
 * applying [block]
 */
fun JibriIq.createResult(block: JibriIq.() -> Unit): JibriIq {
    return JibriIq().apply {
        type = IQ.Type.result
        to = this@createResult.from
        from = this@createResult.to
        stanzaId = this@createResult.stanzaId
        sipAddress = this@createResult.sipAddress
        block()
    }
}

enum class JibriMode(val mode: String) {
    FILE(JibriIq.RecordingMode.FILE.toString()),
    STREAM(JibriIq.RecordingMode.STREAM.toString()),
    SIPGW("sipgw"),
    UNDEFINED(JibriIq.RecordingMode.UNDEFINED.toString())
}

fun JibriIq.mode(): JibriMode {
    if (!sipAddress.isNullOrBlank()) {
        return JibriMode.SIPGW
    }
    return when (recordingMode) {
        JibriIq.RecordingMode.FILE -> JibriMode.FILE
        JibriIq.RecordingMode.STREAM -> JibriMode.STREAM
        else -> JibriMode.UNDEFINED
    }
}

class JibriPresenceHelper {
    companion object {
        fun createPresence(status: JibriStatusPacketExt, to: Jid): Presence {
            val pres = Presence(Presence.Type.available)
            pres.to = to
            pres.overrideExtension(status)
            return pres
        }
    }
}
