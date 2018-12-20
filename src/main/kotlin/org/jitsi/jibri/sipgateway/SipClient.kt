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

package org.jitsi.jibri.sipgateway

import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.util.StatusPublisher

data class SipClientParams(
    /**
     * The SIP address we'll be connecting to
     */
    val sipAddress: String = "",
    /**
     * The display name we'll use for the web conference
     * in the pjsua call
     */
    val displayName: String = ""
)

abstract class SipClient : StatusPublisher<ComponentState>() {
    /**
     * Start the [SipClient]
     */
    abstract fun start()

    /**
     * Stop the [SipClient]
     */
    abstract fun stop()
}
