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

import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.util.PjsuaExitedPrematurely
import org.jitsi.jibri.sipgateway.pjsua.util.RemoteSipClientBusy
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.ProcessExited

data class PjsuaClientParams(
    val sipClientParams: SipClientParams
)

private const val PJSUA_SCRIPT_FILE_LOCATION = "/opt/jitsi/jibri/pjsua.sh"
private const val X_DISPLAY = ":1"

class PjsuaClient(private val pjsuaClientParams: PjsuaClientParams) : SipClient() {
    private val pjsua: JibriSubprocess = JibriSubprocess("pjsua")

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
                        else -> publishStatus(ComponentState.Error(
                            PjsuaExitedPrematurely(processState.runningState.exitCode))
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

        if (pjsuaClientParams.sipClientParams.userName != null &&
            pjsuaClientParams.sipClientParams.password != null) {
            command.add("--id=${pjsuaClientParams.sipClientParams.displayName} " +
                "<sip:${pjsuaClientParams.sipClientParams.userName}>")
            command.add("--registrar=sip:${pjsuaClientParams.sipClientParams.userName.substringAfter('@')}")
            command.add("--realm=*")
            command.add("--username=${pjsuaClientParams.sipClientParams.userName.substringBefore('@')}")
            command.add("--password=${pjsuaClientParams.sipClientParams.password}")
        } else {
            command.add("--id=${pjsuaClientParams.sipClientParams.displayName} <sip:jibri@127.0.0.1>")
        }

        if (pjsuaClientParams.sipClientParams.autoAnswer) {
            command.add("--auto-answer-timer=30")
            command.add("--auto-answer=200")
        } else {
            command.add("sip:${pjsuaClientParams.sipClientParams.sipAddress}")
        }

        pjsua.launch(command, mapOf("DISPLAY" to X_DISPLAY))
    }

    override fun stop() = pjsua.stop()
}
