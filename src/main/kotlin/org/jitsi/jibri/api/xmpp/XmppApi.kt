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
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.AppData
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.getCallUrlInfoFromJid
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
import java.util.logging.Logger

private class UnsupportedIqMode(val iqMode: String) : Exception()

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
    private val jibriStatusManager: JibriStatusManager
) : IQListener {
    private val logger = Logger.getLogger(this::class.qualifiedName)
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
        ProviderManager.addIQProvider(
            JibriIq.ELEMENT_NAME, JibriIq.NAMESPACE, JibriIqProvider()
        )
        updatePresence(jibriStatusManager.overallStatus)
        jibriStatusManager.addStatusHandler(::updatePresence)

        mucClientManager.registerIQ(JibriIq())
        mucClientManager.setIQListener(this)

        // Join all the MUCs we've been told to
        for (config in xmppConfigs) {
            for (host in config.xmppServerHosts) {
                logger.info("Connecting to xmpp environment on $host with config $config")

                // We need to use the host as the ID because we'll only get one MUC client per 'ID' and
                // we may have multiple hosts for the same environment
                val clientConfig = MucClientConfiguration(host).apply {
                    hostname = host
                    domain = config.controlLogin.domain
                    username = config.controlLogin.username
                    password = config.controlLogin.password

                    if (config.trustAllXmppCerts) {
                        logger.info("The trustAllXmppCerts config is enabled for this domain, " +
                                "all XMPP server provided certificates will be accepted")
                        disableCertificateVerification = config.trustAllXmppCerts
                    }

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
    override fun handleIq(iq: IQ, mucClient: MucClient): IQ {
        return if (iq is JibriIq) {
            handleJibriIq(iq, mucClient)
        } else {
            IQ.createErrorResponse(iq, XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))
        }
    }

    /**
     * Helper function to handle a [JibriIq] message with the context of the [XmppEnvironmentConfig] and [MucClient]
     * that this [JibriIq] was received on.
     */
    private fun handleJibriIq(jibriIq: JibriIq, mucClient: MucClient): IQ {
        logger.info("Received JibriIq ${jibriIq.toXML()} from environment $mucClient")
        val xmppEnvironment = xmppConfigs.find { it.xmppServerHosts.contains(mucClient.id) }
                ?: return IQ.createErrorResponse(
                    jibriIq,
                    XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request)
                )
        return when (jibriIq.action) {
            JibriIq.Action.START -> handleStartJibriIq(jibriIq, xmppEnvironment, mucClient)
            JibriIq.Action.STOP -> handleStopJibriIq(jibriIq)
            else -> IQ.createErrorResponse(
                jibriIq,
                XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))
        }
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
            handleStartService(startJibriIq, xmppEnvironment, serviceStatusHandler)
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

    private fun createServiceStatusHandler(request: JibriIq, mucClient: MucClient): JibriServiceStatusHandler {
        return { serviceState ->
            when (serviceState) {
                is ComponentState.Error -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.OFF)) {
                        failureReason = JibriIq.FailureReason.ERROR
                        sipAddress = request.sipAddress
                        shouldRetry = serviceState.error.shouldRetry()
                        logger.info("Current service had an error ${serviceState.error}, " +
                            "sending error iq ${toXML()}")
                        mucClient.sendStanza(this)
                    }
                }
                is ComponentState.Finished -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.OFF)) {
                        sipAddress = request.sipAddress
                        logger.info("Current service finished, sending off iq ${toXML()}")
                        mucClient.sendStanza(this)
                    }
                }
                is ComponentState.Running -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.ON)) {
                        sipAddress = request.sipAddress
                        logger.info("Current service started up successfully, sending on iq ${toXML()}")
                        mucClient.sendStanza(this)
                    }
                }
                else -> {
                    logger.info("XmppAPI ignoring service state update: $serviceState")
                }
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
        serviceStatusHandler: JibriServiceStatusHandler
    ) {
        val callUrlInfo = getCallUrlInfoFromJid(
            startIq.room,
            xmppEnvironment.stripFromRoomDomain,
            xmppEnvironment.xmppDomain
        )
        val appData = startIq.appData?.let {
            jacksonObjectMapper().readValue<AppData>(startIq.appData)
        }
        val serviceParams = ServiceParams(xmppEnvironment.usageTimeoutMins, appData)
        val callParams = CallParams(callUrlInfo)
        logger.info("Parsed call url info: $callUrlInfo")

        when (startIq.mode()) {
            JibriMode.FILE -> {
                jibriManager.startFileRecording(
                    serviceParams,
                    FileRecordingRequestParams(callParams, startIq.sessionId, xmppEnvironment.callLogin),
                    EnvironmentContext(xmppEnvironment.name),
                    serviceStatusHandler
                )
            }
            JibriMode.STREAM -> {
                jibriManager.startStreaming(
                    serviceParams,
                    StreamingParams(
                        callParams,
                        startIq.sessionId,
                        xmppEnvironment.callLogin,
                        youTubeStreamKey = startIq.streamId,
                        youTubeBroadcastId = startIq.youtubeBroadcastId),
                    EnvironmentContext(xmppEnvironment.name),
                    serviceStatusHandler
                )
            }
            JibriMode.SIPGW -> {
                jibriManager.startSipGateway(
                    serviceParams,
                    SipGatewayServiceParams(
                        callParams,
                        xmppEnvironment.callLogin,
                        SipClientParams(startIq.sipAddress, startIq.displayName)),
                    EnvironmentContext(xmppEnvironment.name),
                    serviceStatusHandler
                )
            }
            else -> {
                throw UnsupportedIqMode(startIq.mode().toString())
            }
        }
    }
}
