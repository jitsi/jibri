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
 *
 */

package org.jitsi.jibri.api.xmpp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.JibriBusyException
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.error.BadRequest
import org.jitsi.jibri.error.BadRequestException
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.AppData
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.service.impl.YOUTUBE_URL
import org.jitsi.jibri.service.impl.isRtmpUrl
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.getCallUrlInfoFromJid
import org.jitsi.utils.logging2.createLogger
import org.jitsi.xmpp.extensions.jibri.BadRequestPacketExt
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.xmpp.extensions.jibri.JibriIqProvider
import org.jitsi.xmpp.extensions.jibri.JibriStatusPacketExt
import org.jitsi.xmpp.mucclient.ConnectionStateListener
import org.jitsi.xmpp.mucclient.IQListener
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientConfiguration
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smackx.ping.PingManager
import org.jxmpp.jid.impl.JidCreate
import java.time.Duration
import java.util.concurrent.TimeUnit

private class UnsupportedIqMode(val iqMode: String) : Exception()

private val ASYNC_BAD_REQUEST_REPORT_DELAY: Duration = Duration.ofMillis(200)

/**
 * [XmppApi] connects to XMPP MUCs according to the given [XmppEnvironmentConfig]s (which are
 * parsed from config.json) and listens for IQ messages which contain Jibri commands, which it relays
 * to the given [JibriManager].  The IQ messages are instances of [JibriIq] and allow the
 * starting and stopping of the services Jibri provides.
 * [XmppApi] subscribes to [JibriStatusManager] status updates and translates those into
 * XMPP presence (defined by [JibriStatusPacketExt]) updates to advertise the status of this Jibri.
 * XMPP presence (defined by [JibriStatusPacketExt]) updates to advertise the status of this Jibri.
 * [XmppApi] takes care of translating the XMPP commands into the appropriate
 * [JibriManager] API calls and translates the results into XMPP IQ responses.
 */
class XmppApi(
    private val jibriManager: JibriManager,
    private val xmppConfigs: List<XmppEnvironmentConfig>,
    private val jibriStatusManager: JibriStatusManager,
) : IQListener {
    private val logger = createLogger()

    private val connectionStateListener = object : ConnectionStateListener {
        override fun connected(mucClient: MucClient) {
            jibriManager.jibriMetrics.xmppConnected(mucClient.tags())
        }
        override fun reconnecting(mucClient: MucClient) {
            jibriManager.jibriMetrics.xmppReconnecting(mucClient.tags())
        }
        override fun reconnectionFailed(mucClient: MucClient) {
            jibriManager.jibriMetrics.xmppReconnectionFailed(mucClient.tags())
        }
        override fun pingFailed(mucClient: MucClient) {
            jibriManager.jibriMetrics.xmppPingFailed(mucClient.tags())
        }

        /**
         * If XMPP disconnects we lost our communication channel with jicofo. Jicofo currently doesn't attempt to
         * communicate later, and starts a new session (with a different jibri) immediately. Make sure in this case the
         * recording is stopped.
         */
        override fun closed(mucClient: MucClient) {
            jibriManager.jibriMetrics.xmppClosed(mucClient.tags())
            maybeStop(mucClient)
        }

        /**
         * If XMPP disconnects we lost our communication channel with jicofo. Jicofo currently doesn't attempt to
         * communicate later, and starts a new session (with a different jibri) immediately. Make sure in this case the
         * recording is stopped.
         */
        override fun closedOnError(mucClient: MucClient) {
            jibriManager.jibriMetrics.xmppClosedOnError(mucClient.tags())
            maybeStop(mucClient)
        }

        private fun maybeStop(mucClient: MucClient) {
            val xmppEnvironment = getXmppEnvironment(mucClient) ?: return
            val environmentContext = createEnvironmentContext(xmppEnvironment, mucClient)
            if (jibriManager.currentEnvironmentContext == environmentContext) {
                logger.warn("XMPP disconnected, stopping.")
                jibriManager.jibriMetrics.stoppedOnXmppClosed(mucClient.tags())
                jibriManager.stopService()
            }
        }

        /** Create statsd tags for a [MucClient]. */
        private fun MucClient.tags() = "xmpp_server_host:$id"
    }
    private lateinit var mucClientManager: MucClientManager

    /**
     * Start up the XMPP API by connecting and logging in to all the configured XMPP environments.  For each XMPP
     * connection, we'll listen for incoming [JibriIq] messages and handle them appropriately.  Join the MUC on
     * each connection and send an initial [JibriStatusPacketExt] presence.
     */
    fun start(mucManager: MucClientManager = MucClientManager()) {
        this.mucClientManager = mucManager

        PingManager.setDefaultPingInterval(30)
        JibriStatusPacketExt.registerExtensionProvider()
        BadRequestPacketExt.registerExtensionProvider()
        ProviderManager.addIQProvider(
            JibriIq.ELEMENT,
            JibriIq.NAMESPACE,
            JibriIqProvider()
        )
        updatePresence(jibriStatusManager.overallStatus)
        jibriStatusManager.addStatusHandler(::updatePresence)

        mucClientManager.registerIQ(JibriIq())
        mucClientManager.setIQListener(this)
        mucClientManager.addConnectionStateListener(connectionStateListener)

        // Join all the MUCs we've been told to
        for (config in xmppConfigs) {
            for (host in config.xmppServerHosts) {
                logger.info("Connecting to xmpp environment '${config.name}' on $host")
                val hostDetails: List<String> = host.split(":")

                // We need to use the host as the ID because we'll only get one MUC client per 'ID' and
                // we may have multiple hosts for the same environment
                val clientConfig = MucClientConfiguration(host).apply {
                    hostname = hostDetails[0]
                    domain = config.controlLogin.domain
                    username = config.controlLogin.username
                    password = config.controlLogin.password

                    if (hostDetails.size > 1) {
                        port = hostDetails[1]
                    } else {
                        config.controlLogin.port?.let { port = it.toString() }
                    }

                    if (config.trustAllXmppCerts) {
                        logger.info(
                            "The trustAllXmppCerts config is enabled for this domain, " +
                                "all XMPP server provided certificates will be accepted"
                        )
                        disableCertificateVerification = config.trustAllXmppCerts
                    }

                    if (config.securityMode == ConnectionConfiguration.SecurityMode.disabled) {
                        logger.info(
                            "XMPP security is disabled for this domain, no TLS will be used."
                        )
                    }
                    securityMode = config.securityMode

                    val recordingMucJid =
                        JidCreate.bareFrom("${config.controlMuc.roomName}@${config.controlMuc.domain}").toString()
                    val sipMucJid: String? = config.sipControlMuc?.let {
                        JidCreate.entityBareFrom(
                            "${config.sipControlMuc.roomName}@${config.sipControlMuc.domain}"
                        ).toString()
                    }
                    mucJids = listOfNotNull(recordingMucJid, sipMucJid)
                    mucNickname = config.controlMuc.nickname
                }

                mucClientManager.addMucClient(clientConfig)
            }
        }
    }

    /**
     * Function to update outgoing [presence] stanza with jibri status.
     */
    private fun updatePresence(status: JibriStatus) {
        if (status.shouldBeSentToMuc()) {
            logger.info("Jibri reports its status is now $status, publishing presence to connections")
            mucClientManager.setPresenceExtension(status.toJibriStatusExt())
        } else {
            logger.info("Not forwarding status $status to the MUC")
        }
    }

    /**
     * Handles the JibriIQ.
     *
     * @param iq the IQ to be handled.
     * @param mucClient the [MucClient] from which the IQ comes.
     * @return the IQ to be sent as a response or `null`.
     */
    override fun handleIq(iq: IQ, mucClient: MucClient): IQ = if (iq is JibriIq) {
        handleJibriIq(iq, mucClient)
    } else {
        IQ.createErrorResponse(iq, StanzaError.getBuilder().setCondition(StanzaError.Condition.bad_request).build())
    }

    /**
     * Helper function to handle a [JibriIq] message with the context of the [XmppEnvironmentConfig] and [MucClient]
     * that this [JibriIq] was received on.
     */
    private fun handleJibriIq(jibriIq: JibriIq, mucClient: MucClient): IQ {
        logger.info(
            "Received JibriIq action=${jibriIq.action} mode=${jibriIq.recordingMode} " +
                "room=${jibriIq.room} session=${jibriIq.sessionId} from environment $mucClient"
        )
        val xmppEnvironment = getXmppEnvironment(mucClient)
            ?: return IQ.createErrorResponse(
                jibriIq,
                StanzaError.getBuilder().setCondition(StanzaError.Condition.bad_request).build()
            )
        return when (jibriIq.action) {
            JibriIq.Action.START -> handleStartJibriIq(jibriIq, xmppEnvironment, mucClient)

            JibriIq.Action.STOP -> handleStopJibriIq(jibriIq)

            else -> IQ.createErrorResponse(
                jibriIq,
                StanzaError.getBuilder().setCondition(StanzaError.Condition.bad_request).build()
            )
        }
    }

    private fun getXmppEnvironment(mucClient: MucClient) = xmppConfigs.find {
        it.xmppServerHosts.contains(mucClient.id)
    }

    /**
     * Handle a start [JibriIq] message.  We'll respond immediately with a [JibriIq.Status.PENDING] IQ response and
     * send a new IQ with the subsequent stats after starting the service:
     * [JibriIq.Status.OFF] if there was an error starting the service (or an error while the service was running).
     *  In this case, a [JibriIq.FailureReason] will be set as well.
     * [JibriIq.Status.ON] if the service started successfully
     */
    private fun handleStartJibriIq(
        startJibriIq: JibriIq,
        xmppEnvironment: XmppEnvironmentConfig,
        mucClient: MucClient
    ): IQ {
        logger.info("Received start request, starting service")
        // If there is an issue with the service while it's running, we need to send an IQ
        // to notify the caller who invoked the service of its status, so we'll listen
        // for the service's status while it's running and this method will be invoked
        // if it changes
        val serviceStatusHandler = createServiceStatusHandler(startJibriIq, mucClient)
        return try {
            handleStartService(
                startJibriIq,
                xmppEnvironment,
                createEnvironmentContext(xmppEnvironment, mucClient),
                serviceStatusHandler
            )
            logger.info("Sending 'pending' response to start IQ")
            startJibriIq.createResult {
                status = JibriIq.Status.PENDING
            }
        } catch (busy: JibriBusyException) {
            logger.error("Jibri is currently busy, cannot service this request")
            startJibriIq.createResult {
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.BUSY
                shouldRetry = true
            }
        } catch (e: BadRequestException) {
            rejectBadRequest(startJibriIq, serviceStatusHandler, e)
        } catch (iq: UnsupportedIqMode) {
            logger.error("Unsupported IQ mode: ${iq.iqMode}")
            startJibriIq.createResult {
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.ERROR
                shouldRetry = false
            }
        } catch (t: Throwable) {
            logger.error("Error starting Jibri service ", t)
            startJibriIq.createResult {
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.ERROR
                shouldRetry = true
            }
        }
    }

    /**
     * Refuses [startJibriIq] because the request is invalid.
     *
     * How we say so depends on whether the requester told us it understands a [BadRequestPacketExt] in the response.
     * A Jicofo release which does not treats any response other than 'pending' as unexpected: it marks this healthy
     * instance as failed and retries the same doomed request with other instances. So unless it opted in, we answer
     * 'pending' and then report the failure on the asynchronous path, which every release already handles by giving
     * up on the request.
     */
    private fun rejectBadRequest(
        startJibriIq: JibriIq,
        serviceStatusHandler: JibriServiceStatusHandler,
        e: BadRequestException
    ): JibriIq = if (startJibriIq.supportsBadRequest == true) {
        startJibriIq.createResult {
            status = JibriIq.Status.OFF
            failureReason = JibriIq.FailureReason.ERROR
            shouldRetry = false
            addExtension(BadRequestPacketExt(e.detail))
        }
    } else {
        // The 'pending' result below is sent by Smack only after this whole call returns, and Smack gives us no
        // callback for when that has actually happened. So this cannot be a hard ordering guarantee: it is a short
        // delay, long enough to beat handing a small stanza to the OS socket (microseconds), not a network round
        // trip. This path is only exercised by a Jicofo release old enough not to set supportsBadRequest, so it
        // stops being exercised at all once that release is upgraded.
        TaskPools.recurringTasksPool.schedule(
            { serviceStatusHandler(ComponentState.Error(BadRequest(e.detail))) },
            ASYNC_BAD_REQUEST_REPORT_DELAY.toMillis(),
            TimeUnit.MILLISECONDS
        )
        startJibriIq.createResult { status = JibriIq.Status.PENDING }
    }

    private fun createServiceStatusHandler(request: JibriIq, mucClient: MucClient): JibriServiceStatusHandler =
        { serviceState ->
            when (serviceState) {
                is ComponentState.Error -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.OFF)) {
                        failureReason = JibriIq.FailureReason.ERROR
                        sipAddress = request.sipAddress
                        shouldRetry = serviceState.error.shouldRetry()
                        // Only when the requester opted in. A release without a provider for the element logs a
                        // warning for every one it receives.
                        if (request.supportsBadRequest == true && serviceState.error is BadRequest) {
                            addExtension(BadRequestPacketExt(serviceState.error.detail))
                        }
                        logger.info(
                            "Current service had an error ${serviceState.error}, " +
                                "sending error iq status=$status failureReason=$failureReason shouldRetry=$shouldRetry"
                        )
                        mucClient.sendStanza(this)
                    }
                }

                is ComponentState.Finished -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.OFF)) {
                        sipAddress = request.sipAddress
                        logger.info("Current service finished, sending off iq status=$status")
                        mucClient.sendStanza(this)
                    }
                }

                is ComponentState.Running -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.ON)) {
                        sipAddress = request.sipAddress
                        logger.info("Current service started up successfully, sending on iq status=$status")
                        mucClient.sendStanza(this)
                    }
                }

                else -> {
                    logger.info("XmppAPI ignoring service state update: $serviceState")
                }
            }
        }

    /**
     * Handle a stop [JibriIq] message to stop the currently running service (if there is one).  Send a [JibriIq]
     * response with [JibriIq.Status.OFF].
     */
    private fun handleStopJibriIq(stopJibriIq: JibriIq): IQ {
        jibriManager.stopService()
        // By this point the service has been fully stopped
        return stopJibriIq.createResult {
            status = JibriIq.Status.OFF
        }
    }

    /**
     * Helper function to actually start the service.  We need to parse the fields in the [JibriIq] message
     * to determine which [JibriManager] service API to call, as well as convert the types into what [JibriManager]
     * expects
     */
    private fun handleStartService(
        startIq: JibriIq,
        xmppEnvironment: XmppEnvironmentConfig,
        environmentContext: EnvironmentContext,
        serviceStatusHandler: JibriServiceStatusHandler
    ) {
        val callUrlInfo = getCallUrlInfoFromJid(
            startIq.room,
            xmppEnvironment.stripFromRoomDomain,
            xmppEnvironment.xmppDomain,
            xmppEnvironment.baseUrl
        )
        val appData = startIq.appData?.let {
            jacksonObjectMapper().readValue<AppData>(startIq.appData)
        }
        val serviceParams = ServiceParams(xmppEnvironment.usageTimeoutMins, appData)
        val extraUrlParams = buildList {
            startIq.rtcStatsEnabled?.let { add("config.analytics.rtcstatsEnabled=$it") }
        }
        val callParams = CallParams(callUrlInfo, extraUrlParams = extraUrlParams)
        logger.info("Parsed call url info: $callUrlInfo")

        when (startIq.mode()) {
            JibriMode.FILE -> {
                jibriManager.startFileRecording(
                    serviceParams,
                    FileRecordingRequestParams(callParams, startIq.sessionId, xmppEnvironment.callLogin),
                    environmentContext,
                    serviceStatusHandler
                )
            }

            JibriMode.STREAM -> {
                val rtmpUrl = if (startIq.streamId.isRtmpUrl()) {
                    startIq.streamId
                } else {
                    "$YOUTUBE_URL/${startIq.streamId}"
                }
                val viewingUrl = if (startIq.youtubeBroadcastId != null) {
                    if (startIq.youtubeBroadcastId.isViewingUrl()) {
                        startIq.youtubeBroadcastId
                    } else {
                        "http://youtu.be/${startIq.youtubeBroadcastId}"
                    }
                } else {
                    null
                }
                // The stream key is a credential; redact it before logging the URL.
                logger.info("Using RTMP URL ${rtmpUrl.redactStreamKey()} and viewing URL $viewingUrl")
                jibriManager.startStreaming(
                    serviceParams,
                    StreamingParams(
                        callParams,
                        startIq.sessionId,
                        xmppEnvironment.callLogin,
                        rtmpUrl = rtmpUrl,
                        viewingUrl = viewingUrl
                    ),
                    environmentContext,
                    serviceStatusHandler
                )
            }

            JibriMode.SIPGW -> {
                jibriManager.startSipGateway(
                    serviceParams,
                    SipGatewayServiceParams(
                        callParams,
                        xmppEnvironment.callLogin,
                        SipClientParams(startIq.sipAddress, startIq.displayName)
                    ),
                    environmentContext,
                    serviceStatusHandler
                )
            }

            else -> {
                throw UnsupportedIqMode(startIq.mode().toString())
            }
        }
    }
}

private fun String.isViewingUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/** Replaces the last path segment (the stream key, for an RTMP URL) with a placeholder, for logging. */
private fun String.redactStreamKey(): String = substringBeforeLast('/') + "/****"

private fun createEnvironmentContext(xmppEnvironment: XmppEnvironmentConfig, mucClient: MucClient) =
    EnvironmentContext("${xmppEnvironment.name}-${mucClient.id}")
