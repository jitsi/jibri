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
import org.jitsi.jibri.util.StatusPublisher

data class SipClientParams(
    /**
     * The SIP address we'll be connecting to
     */
    val sipAddress: String = "",

    /**
     * The display name used by pjsua as identity when listening for or sending an invite
     * For sending an invite, this should be the name of the entity initiating the invite
     */
    val displayName: String = "",

    /**
     * Whether auto-answer is enabled, if it is, the client will listen for
     * incoming invites and will auto answer the first one.
     */
    val autoAnswer: Boolean = false,

    /**
     * The optional auto-answer-timer in seconds.
     * If auto-answer is enabled, the client will listen for incoming invites
     * during this time.
     */
    val autoAnswerTimer: Long? = 30,

    /**
     * The username to use if registration is needed.
     */
    val userName: String? = null,

    /**
     * The password to use if registration is needed.
     */
    val password: String? = null,

    /**
     * The optional contact address the invitee will be connecting to
     */
    val contact: String? = null,

    /**
     * The optional address of proxy server
     */
    val proxy: String? = null
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

/**
 * A SIP address is written in user@domain.tld format in a similar fashion to an email address.
 * An address like: sip:1-999-123-4567@voip-provider.example.net
 */
fun String.getSipAddress(): String {
    if (this.isNotEmpty() && this.hasSipSchemeEmbedded()) {
        return this.substringAfter(":")
    }
    return this
}

/**
 * Valid options would be `sip` or `sips`
 * The default sip scheme is `sip` if none is specified
 */
fun String.getSipScheme(): String {
    if (this.isNotEmpty() && this.hasSipSchemeEmbedded()) {
        return this.substringBefore(":")
    }
    return "sip"
}

private fun String.hasSipSchemeEmbedded(): Boolean =
    contains("sip:", ignoreCase = true) || contains("sips:", ignoreCase = true)
