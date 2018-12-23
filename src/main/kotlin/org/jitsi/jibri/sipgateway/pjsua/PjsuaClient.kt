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

import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.ProcessExited

data class PjsuaClientParams(
    val sipClientParams: SipClientParams
)

private const val CAPTURE_DEVICE = 23
private const val PLAYBACK_DEVICE = 24
private const val CONFIG_FILE_LOCATION = "/home/jibri/pjsua.config"
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
                        //TODO: add detail?
                        // Remote side hung up
                        0 -> publishStatus(ComponentState.Finished)
                        2 -> publishStatus(ComponentState.Error(ErrorScope.SESSION, "Remote side busy"))
                        else -> publishStatus(ComponentState.Error(ErrorScope.SESSION, "Pjsua exited with code ${processState.runningState.exitCode}"))
                    }
                }
                //TODO: i think everything else just counts as running?
                else -> publishStatus(ComponentState.Running)
            }
        }
    }

    override fun start() {
        val command = listOf(
                "pjsua",
                "--capture-dev=$CAPTURE_DEVICE",
                "--playback-dev=$PLAYBACK_DEVICE",
                "--id", "${pjsuaClientParams.sipClientParams.displayName} <sip:jibri@127.0.0.1>",
                "--config-file", CONFIG_FILE_LOCATION,
                "--log-file", "/tmp/pjsua.out",
                "sip:${pjsuaClientParams.sipClientParams.sipAddress}"
        )
        pjsua.launch(command, mapOf("DISPLAY" to X_DISPLAY))
    }

    override fun stop() = pjsua.stop()
}
