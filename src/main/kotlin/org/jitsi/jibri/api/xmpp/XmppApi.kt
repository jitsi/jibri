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

package org.jitsi.jibri.api.xmpp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jitsi.jibri.AppData
import org.jitsi.jibri.EnvironmentContext
import org.jitsi.jibri.JibriBusy
import org.jitsi.jibri.JibriError
import org.jitsi.jibri.JibriException
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.JibriSession
import org.jitsi.jibri.JibriState
import org.jitsi.jibri.JobParams
import org.jitsi.jibri.UnsupportedIqMode
import org.jitsi.jibri.job.streaming.StreamingParams
import org.jitsi.jibri.job.streaming.YOUTUBE_URL
import org.jitsi.jibri.job.streaming.isRtmpUrl
import org.jitsi.jibri.job.streaming.isViewingUrl
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.shouldRetry
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.utils.logging2.createLogger
import org.jitsi.xmpp.extensions.health.HealthStatusPacketExt
import org.jitsi.xmpp.extensions.health.HealthStatusPacketExt.Health
import org.jitsi.xmpp.extensions.jibri.JibriBusyStatusPacketExt
import org.jitsi.xmpp.extensions.jibri.JibriBusyStatusPacketExt.BusyStatus
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.xmpp.extensions.jibri.JibriIqProvider
import org.jitsi.xmpp.extensions.jibri.JibriStatusPacketExt
import org.jitsi.xmpp.mucclient.IQListener
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientConfiguration
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smackx.ping.PingManager
import org.jxmpp.jid.impl.JidCreate
import kotlin.time.minutes

class XmppApi(
    private val jibriManager: JibriManager,
    private val xmppConfigs: List<XmppEnvironment>,
    private val mucClientManager: MucClientManager = MucClientManager(),
    private val scope: CoroutineScope = CoroutineScope(CoroutineName("XMPP API"))
) {
    private val logger = createLogger()
    private val iqListener = IqListener()
    private val sessions: MutableMap<String, JibriSession> = mutableMapOf()

    init {
        PingManager.setDefaultPingInterval(30)
        JibriStatusPacketExt.registerExtensionProvider()
        ProviderManager.addIQProvider(
            JibriIq.ELEMENT_NAME, JibriIq.NAMESPACE, JibriIqProvider()
        )

        mucClientManager.registerIQ(JibriIq())
        mucClientManager.setIQListener(iqListener)

        scope.launch(CoroutineName("XMPP presence updater")) {
            // Monitor the state and update our presence when it changes
            jibriManager.currentState.collect {
                logger.info("Saw Jibri's state change to $it")
                updatePresence(it)
            }
        }
    }

    fun joinMucs() {
        for (env in xmppConfigs) {
            for (host in env.xmppServerHosts) {
                logger.info("Connecting to xmpp environment on $host with config $env")

                // We need to use the host as the ID because we'll only get one MUC client per 'ID' and
                // we may have multiple hosts for the same environment
                val clientConfig = MucClientConfiguration(host).apply {
                    hostname = host
                    domain = env.controlLogin.domain
                    username = env.controlLogin.username
                    password = env.controlLogin.password

                    if (env.trustAllXmppCerts) {
                        logger.info(
                            "The trustAllXmppCerts config is enabled for this domain, " +
                                "all XMPP server provided certificates will be accepted"
                        )
                        disableCertificateVerification = env.trustAllXmppCerts
                    }

                    val recordingMucJid =
                        JidCreate.bareFrom("${env.controlMuc.roomName}@${env.controlMuc.domain}").toString()
                    val sipMucJid: String? = env.sipControlMuc?.let {
                        JidCreate.entityBareFrom(
                            "${env.sipControlMuc.roomName}@${env.sipControlMuc.domain}"
                        ).toString()
                    }
                    mucJids = listOfNotNull(recordingMucJid, sipMucJid)
                    mucNickname = env.controlMuc.nickname
                }

                mucClientManager.addMucClient(clientConfig)
            }
        }
    }

    /**
     * Handle a [JibriIq] message with the context of the [XmppEnvironmentConfig] and [MucClient]
     * that this [JibriIq] was received on.
     */
    private fun handleJibriIq(jibriIq: JibriIq, mucClient: MucClient): IQ {
        logger.debug { "Got Jibri IQ: ${jibriIq.toXML()}" }
        val xmppEnvironment = xmppConfigs.find { it.xmppServerHosts.contains(mucClient.id) }
            ?: return badRequest(jibriIq)
        return when (jibriIq.action) {
            JibriIq.Action.START -> {
                try {
                    handleStartJibriIq(jibriIq, xmppEnvironment, mucClient)
                } catch (b: JibriBusy) {
                    jibriIq.createResult {
                        status = JibriIq.Status.OFF
                        failureReason = JibriIq.FailureReason.BUSY
                        shouldRetry = true
                    }
                }
            }
            JibriIq.Action.STOP -> {
                val job = sessions[jibriIq.sessionId] ?: run {
                    logger.error("Got request to stop unknown session ${jibriIq.sessionId}")
                    return badRequest(jibriIq)
                }
                logger.info("Stopping session ${jibriIq.sessionId}")
                job.cancel("Stopped externally")
                jibriIq.createResult {
                    status = JibriIq.Status.OFF
                }
            }
            else -> badRequest(jibriIq)
        }
    }

    private fun handleStartJibriIq(
        request: JibriIq,
        environment: XmppEnvironment,
        mucClient: MucClient
    ): IQ {
        logger.info("Received start request, starting service")
        val session = try {
            startSession(request, environment)
        } catch (j: JibriException) {
            return createOffIqUpdateFrom(j, request)
        } catch (t: Throwable) {
            logger.error("Should never get here, saw non JibriException", t)
            TODO()
        }

        logger.info("Started session ${request.sessionId}")
        sessions[request.sessionId] = session

        // Launch a coroutine to monitor the job.  We create an empty exception handler to swallow the exception
        // since we handle it separately (the handling code still needs to throw the exception to cancel the coroutine
        // properly)
        val handler = CoroutineExceptionHandler { _, _ -> }
        scope.launch(CoroutineName("XMPP session ${request.sessionId} monitor") + handler) {
            coroutineScope {
                launch(CoroutineName("XMPP session ${request.sessionId} wait for running")) {
                    // When the session transitions to 'running', send an update
                    session.onRunning {
                        logger.info("Session ${request.sessionId} has transitioned to running, sending update")
                        mucClient.sendStanza(createOnIqUpdateFrom(request))
                    }
                }
                launch(CoroutineName("XMPP session ${request.sessionId} wait for finish")) {
                    try {
                        session.await()
                    } catch (j: JibriException) {
                        logger.info("Session ${request.sessionId} finished (${j.message}), sending update")
                        mucClient.sendStanza(createOffIqUpdateFrom(j, request))
                        throw j
                    }
                }
            }
        }

        return request.createResult {
            status = JibriIq.Status.PENDING
        }
    }

    /**
     * Function to update outgoing presence stanza with Jibri status.
     */
    private fun updatePresence(state: JibriState) {
        state.toJibriStatusExt()?.let {
            logger.info("Jibri reports its status is now $state, publishing presence to connections")
            mucClientManager.setPresenceExtension(it)
        }
    }

    private fun startSession(
        request: JibriIq,
        environment: XmppEnvironment
    ): JibriSession {
        val callUrlInfo = getCallUrlInfoFromJid(
            request.room,
            environment.stripFromRoomDomain,
            environment.xmppDomain
        )
        val appData = request.appData?.let {
            jacksonObjectMapper().readValue<AppData>(request.appData)
        }
        val callParams = CallParams(callUrlInfo, environment.callLogin)
        val jobParams = JobParams(request.sessionId, environment.usageTimeoutMins.minutes, callParams, appData)
        logger.info("Parsed call url info: $callUrlInfo")

        return when (request.mode()) {
            JibriMode.FILE -> {
                jibriManager.startFileRecordingSession(
                    jobParams,
                    EnvironmentContext(environment.name),
                )
            }
            JibriMode.STREAM -> {
                val rtmpUrl = request.getRtmpUrl()
                val viewingUrl = request.getViewingUrl()
                logger.info("Using RTMP URL $rtmpUrl and viewing URL $viewingUrl")
                jibriManager.startStreamingSession(
                    jobParams,
                    StreamingParams(rtmpUrl, viewingUrl),
                    EnvironmentContext(environment.name),
                )
            }
            JibriMode.SIPGW -> {
                jibriManager.startSipGwSession(
                    jobParams,
                    SipClientParams(sipAddress = request.sipAddress, displayName = request.displayName),
                    EnvironmentContext(environment.name),
                )
            }
            else -> {
                throw UnsupportedIqMode(request.mode())
            }
        }
    }

    private inner class IqListener : IQListener {
        override fun handleIq(iq: IQ, mucClient: MucClient): IQ {
            return if (iq is JibriIq) {
                handleJibriIq(iq, mucClient)
            } else {
                IQ.createErrorResponse(iq, XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))
            }
        }
    }
}

private fun badRequest(iq: IQ): IQ =
    IQ.createErrorResponse(iq, XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))

/**
 * Convert a [JibriState] to [JibriStatusPacketExt].  If the [JibriState] shouldn't
 * be sent as a [JibriStatusPacketExt], return null
 */
private fun JibriState.toJibriStatusExt(): JibriStatusPacketExt? {
    return when (this) {
        is JibriState.Busy -> createJibriStatusExt(BusyStatus.BUSY, Health.HEALTHY)
        is JibriState.Idle -> createJibriStatusExt(BusyStatus.IDLE, Health.HEALTHY)
        is JibriState.Error -> createJibriStatusExt(BusyStatus.IDLE, Health.UNHEALTHY)
        // 'Expired' is not a state we reflect in the control MUC (and isn't
        // defined by [JibriStatusPacketExt]), it's used only for the
        // internal health status so for now we don't see it to the MUC.
        is JibriState.Expired -> null
    }
}

private fun createJibriStatusExt(
    busyStatusExt: BusyStatus,
    healthStatusExt: Health
): JibriStatusPacketExt {
    return JibriStatusPacketExt().apply {
        busyStatus = JibriBusyStatusPacketExt().apply { status = busyStatusExt }
        healthStatus = HealthStatusPacketExt().apply { status = healthStatusExt }
    }
}

private fun JibriIq.getRtmpUrl(): String {
    return if (streamId.isRtmpUrl()) {
        streamId
    } else {
        "$YOUTUBE_URL/$streamId"
    }
}

private fun JibriIq.getViewingUrl(): String? {
    return if (youtubeBroadcastId != null) {
        if (youtubeBroadcastId.isViewingUrl()) {
            youtubeBroadcastId
        } else {
            "http://youtu.be/$youtubeBroadcastId"
        }
    } else {
        null
    }
}

private fun createOnIqUpdateFrom(originalRequest: JibriIq): JibriIq =
    createIqUpdateFrom(null, JibriIq.Status.ON, originalRequest)

private fun createOffIqUpdateFrom(j: JibriException, originalRequest: JibriIq): JibriIq =
    createIqUpdateFrom(j, JibriIq.Status.OFF, originalRequest)

private fun createIqUpdateFrom(j: JibriException?, status: JibriIq.Status, originalRequest: JibriIq): JibriIq {
    return JibriIq().apply {
        to = originalRequest.from
        sipAddress = originalRequest.sipAddress
        type = IQ.Type.set
        this.status = status
        if (j is JibriError) {
            failureReason = if (j is JibriBusy) JibriIq.FailureReason.BUSY else JibriIq.FailureReason.ERROR
            shouldRetry = j.shouldRetry()
        }
    }
}
