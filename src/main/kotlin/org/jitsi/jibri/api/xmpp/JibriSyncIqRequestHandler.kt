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
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XMPPError

abstract class JibriSyncIqRequestHandler : AbstractIqRequestHandler(
        JibriIq.ELEMENT_NAME,
        JibriIq.NAMESPACE,
        IQ.Type.set,
        IQRequestHandler.Mode.sync) {
    override fun handleIQRequest(iq: IQ): IQ {
        return if (iq is JibriIq) {
            handleJibriIqRequest(iq)
        } else {
            IQ.createErrorResponse(iq, XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))
        }
    }

    abstract fun handleJibriIqRequest(jibriIq: JibriIq): IQ
}
