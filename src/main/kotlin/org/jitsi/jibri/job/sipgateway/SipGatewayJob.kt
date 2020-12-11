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

package org.jitsi.jibri.job.sipgateway

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jitsi.jibri.ProcessFailedToStart
import org.jitsi.jibri.job.IntermediateJobState
import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.Running
import org.jitsi.jibri.job.StartingUp
import org.jitsi.jibri.pjsua.PJSUA_X_DISPLAY
import org.jitsi.jibri.pjsua.PjsuaFileHandler
import org.jitsi.jibri.pjsua.PjsuaHelpers
import org.jitsi.jibri.pjsua.getPjsuaCommand
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.Selenium
import org.jitsi.jibri.selenium.SeleniumOptions
import org.jitsi.jibri.selenium.SipGatewayUrlOptions
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.util.getLoggerWithHandler
import org.jitsi.jibri.util.logProcessOutput
import org.jitsi.jibri.util.runProcess
import org.jitsi.jibri.util.stopProcess
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.utils.mins

class SipGatewayJob(
    parentLogger: Logger,
    private val sessionId: String,
    private val callParams: CallParams,
    private val sipClientParams: SipClientParams
) : JibriJob {
    private val logger = createChildLogger(parentLogger, mapOf("session-id" to sessionId))
    private val _state = MutableStateFlow<IntermediateJobState>(StartingUp)
    override val state: StateFlow<IntermediateJobState> = _state.asStateFlow()
    override val name: String = "Sip GW job $sessionId"

    private val selenium = Selenium(
        logger,
        SeleniumOptions(
            displayName = sipClientParams.displayName,
            // by default we wait 30 minutes alone in the call before deciding to hangup
            emptyCallTimeout = 30.mins,
            extraChromeCommandLineFlags = listOf("--alsa-input-device=plughw:1,1")
        )
    )

    override suspend fun run() {
        coroutineScope {
            val joinCallJob = launch {
                selenium.joinCall(
                    callUrlInfo = callParams.callUrlInfo.copy(urlParams = SipGatewayUrlOptions),
                    callParams.callLogin
                )
            }
            if (!sipClientParams.autoAnswer) {
                // If we're not in auto-answer, we'll wait for selenium to join the call before
                // starting pjsua
                joinCallJob.join()
            }
            val pjsuaCommand = getPjsuaCommand(sipClientParams)
            logger.info("Starting pjsua via ${pjsuaCommand.joinToString(separator = " ")} ($pjsuaCommand)")
            val pjsua = try {
                runProcess(
                    pjsuaCommand,
                    environment = mapOf("DISPLAY" to PJSUA_X_DISPLAY)
                )
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    selenium.leaveCallAndQuitBrowser()
                }
                throw ProcessFailedToStart("Pjsua failed to start: ${t.message}")
            }
            // We don't monitor Pjsua's startup logs, so if it started we consider it running
            _state.value = Running
            val pjsuaLogOutputTask = logProcessOutput(pjsua, getLoggerWithHandler("pjsua", PjsuaFileHandler))
            try {
                coroutineScope {
                    launch { PjsuaHelpers.watchForProcessError(pjsua) }
                    launch { selenium.monitorCall() }
                }
            } finally {
                withContext(NonCancellable) {
                    stopProcess(pjsua)
                    selenium.leaveCallAndQuitBrowser()
                    pjsuaLogOutputTask.join()
                }
            }
        }
    }
}
