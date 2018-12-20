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

import org.jitsi.jibri.capture.ffmpeg.executor.ErrorScope
import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.executor.PjsuaExecutor
import org.jitsi.jibri.sipgateway.pjsua.executor.PjsuaExecutorParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.ProcessExited

data class PjsuaClientParams(
    val sipClientParams: SipClientParams
)

class PjsuaClient(private val pjsuaClientParams: PjsuaClientParams) : SipClient() {
    private val pjsuaExecutor = PjsuaExecutor()

    companion object {
        const val COMPONENT_ID = "Pjsua"
    }

    init {
        pjsuaExecutor.addStatusHandler { processState ->
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

    override fun start() = pjsuaExecutor.launchPjsua(PjsuaExecutorParams(pjsuaClientParams.sipClientParams))

    override fun stop() = pjsuaExecutor.stopPjsua()
}
