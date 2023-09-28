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

package org.jitsi.jibri.sipgateway.pjsua

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.getSipAddress
import org.jitsi.jibri.sipgateway.getSipScheme
import org.jitsi.jibri.sipgateway.pjsua.util.PjsuaExitedPrematurely
import org.jitsi.jibri.sipgateway.pjsua.util.RemoteSipClientBusy
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.ProcessExited
import org.jitsi.metaconfig.from
import org.jitsi.metaconfig.optionalconfig
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger

data class PjsuaClientParams(
    val sipClientParams: SipClientParams
)

private const val PJSUA_SCRIPT_FILE_LOCATION = "/opt/jitsi/jibri/pjsua.sh"
private const val X_DISPLAY = ":1"

class PjsuaClient(
    parentLogger: Logger,
    private val pjsuaClientParams: PjsuaClientParams
) : SipClient() {
    private val logger = createChildLogger(parentLogger)
    private val pjsua: JibriSubprocess = JibriSubprocess(logger, "pjsua")
    private val sipOutboundPrefix: String? by optionalconfig(
        "jibri.sip.outbound-prefix".from(Config.configSource)
    )

    companion object {
        const val COMPONENT_ID = "Pjsua"
    }

    init {
        pjsua.addStatusHandler { processState ->
            when {
                processState.runningState is ProcessExited -> {
                    when (processState.runningState.exitCode) {
                        // TODO: add detail?
                        // Remote side hung up
                        0 -> publishStatus(ComponentState.Finished)
                        2 -> publishStatus(ComponentState.Error(RemoteSipClientBusy))
                        else -> publishStatus(
                            ComponentState.Error(PjsuaExitedPrematurely(processState.runningState.exitCode))
                        )
                    }
                }
                // TODO: i think everything else just counts as running?
                else -> publishStatus(ComponentState.Running)
            }
        }
    }

    override fun start() {
        val command = mutableListOf(
            PJSUA_SCRIPT_FILE_LOCATION
        )

        val sipAddress = pjsuaClientParams.sipClientParams.sipAddress.getSipAddress()
        val sipScheme = pjsuaClientParams.sipClientParams.sipAddress.getSipScheme()

        if (pjsuaClientParams.sipClientParams.userName != null &&
            pjsuaClientParams.sipClientParams.password != null
        ) {
            command.add(
                "--id=${pjsuaClientParams.sipClientParams.displayName} " +
                    "<$sipScheme:${pjsuaClientParams.sipClientParams.userName}>"
            )
            command.add(
                "--registrar=$sipScheme:${
                    pjsuaClientParams.sipClientParams.userName.substringAfter('@')
                }"
            )
            command.add("--realm=*")
            command.add("--username=${pjsuaClientParams.sipClientParams.userName.substringBefore('@')}")
            command.add("--password=${pjsuaClientParams.sipClientParams.password}")
        }

        if (!pjsuaClientParams.sipClientParams.contact.isNullOrEmpty()) {
            command.add("--contact=${pjsuaClientParams.sipClientParams.contact}")
        }

        if (!pjsuaClientParams.sipClientParams.proxy.isNullOrEmpty()) {
            command.add("--proxy=${pjsuaClientParams.sipClientParams.proxy}")
        }

        if (pjsuaClientParams.sipClientParams.autoAnswer) {
            command.add("--auto-answer-timer=${pjsuaClientParams.sipClientParams.autoAnswerTimer}")
            command.add("--auto-answer=200")
        } else {
            // The proxy we'll use for all the outgoing SIP requests;
            // This proxy will be enabled if --proxy is not set explicitly through API
            // The client should not specify a Route header in the sip INVITE message. Using hide will let the server set the Route header
            if (pjsuaClientParams.sipClientParams.proxy == null &&
                pjsuaClientParams.sipClientParams.userName != null
            ) {
                command.add(
                    "--proxy=$sipScheme:${pjsuaClientParams.sipClientParams.userName.substringAfter('@')};" +
                        "transport=tcp;hide"
                )
            }

            if (sipOutboundPrefix.isNullOrEmpty()) {
                command.add("$sipScheme:$sipAddress")
            } else {
                command.add("$sipScheme:${sipOutboundPrefix}$sipAddress")
            }
        }

        pjsua.launch(command, mapOf("DISPLAY" to X_DISPLAY))
    }

    override fun stop() = pjsua.stop()
}
