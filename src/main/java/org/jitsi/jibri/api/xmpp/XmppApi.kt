package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIqProvider
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriStatusPacketExt
import org.jitsi.jibri.*
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.xmpp.mucclient.MucClient
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.util.concurrent.Executors
import java.util.logging.Logger

/**
 * [XmppApi] connects to an XMPP MUC according to the given [xmppConfig] and listens
 * for IQ messages which contain Jibri commands, which it relays
 * to the given [jibriManager].  It takes care of translating the XMPP commands into the appropriate
 * [JibriManager] API calls and translates the results into XMPP IQ responses.
 */
class XmppApi(
        private val jibriManager: JibriManager,
        xmppConfigs: List<XmppEnvironmentConfig>
) {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val executor = Executors.newSingleThreadExecutor()

    init {
        JibriStatusPacketExt.registerExtensionProvider()
        ProviderManager.addIQProvider(
            JibriIq.ELEMENT_NAME, JibriIq.NAMESPACE, JibriIqProvider()
        )

        // Join all the mucs we've been told to
        for (config in xmppConfigs) {
            for (host in config.xmppServerHosts) {
                logger.info("Connecting to xmpp environment on $host with config $config")
                val mucClient = MucClient(XMPPTCPConnectionConfiguration.builder()
                    .setHost(host)
                    .setXmppDomain(config.controlLogin.domain)
                    .setUsernameAndPassword(config.controlLogin.username, config.controlLogin.password)
                    .build())
                mucClient.addIqRequestHandler(object: JibriSyncIqRequestHandler() {
                    override fun handleJibriIqRequest(jibriIq: JibriIq): IQ {
                        return handleJibriIq(jibriIq, config, mucClient)
                    }
                })
                mucClient.createOrJoinMuc(JidCreate.entityBareFrom("${config.controlMuc.roomName}@${config.controlMuc.domain}"),
                    Resourcepart.from(config.controlMuc.nickname))
                val jibriStatus = JibriPresenceHelper.createPresence(
                    JibriStatusPacketExt.Status.IDLE,
                    JidCreate.bareFrom("${config.controlMuc.roomName}@${config.controlMuc.domain}"))
                mucClient.sendStanza(jibriStatus)
            }
        }
    }

    private fun handleJibriIq(jibriIq: JibriIq, xmppEnvironment: XmppEnvironmentConfig, mucClient: MucClient): IQ {
        logger.info("Received JibriIq")
        when (jibriIq.action) {
            JibriIq.Action.START -> {
                logger.info("Received start request")
                // Immediately respond that the request is pending
                val initialResponse = JibriIqHelper.createResult(jibriIq)
                initialResponse.status = JibriIq.Status.PENDING
                // Start the actual service and send an IQ once we get the result
                executor.submit {
                    val resultIq = JibriIq()
                    resultIq.to = jibriIq.from
                    resultIq.type = IQ.Type.set
                    logger.info("Starting service")
                    resultIq.status = when (handleStartService(jibriIq, xmppEnvironment)) {
                        StartServiceResult.SUCCESS -> JibriIq.Status.ON
                        StartServiceResult.BUSY -> JibriIq.Status.BUSY
                        StartServiceResult.ERROR -> JibriIq.Status.FAILED
                    }
                    logger.info("Sending 'on' iq")
                    mucClient.sendStanza(resultIq)
                }
                logger.info("Sending 'pending' response to start IQ")
                return initialResponse
            }
            JibriIq.Action.STOP -> {
                jibriManager.stopService()
                // By this point the service has been fully stopped
                val response = JibriIqHelper.createResult(jibriIq)
                response.status = JibriIq.Status.OFF
                return response
            }
            else -> {
                return IQ.createErrorResponse(jibriIq, XMPPError.getBuilder().setCondition(XMPPError.Condition.bad_request))
            }
        }
    }

    private fun handleStartService(startIq: JibriIq, xmppEnvironment: XmppEnvironmentConfig): StartServiceResult {
        val jibriDisplayName = startIq.displayName
        /**
         * The call url is constructed from the xmpp domain, an optional subdomain, and a callname like so:
         * https://domain/subdomain/callName
         * The domain is pulled from the xmpp domain of the connection sending the request
         */
        var domain = startIq.room.domain.toString()
        // First strip out any string we've been told to remove from the domain
        domain = domain.replace(xmppEnvironment.stripFromRoomDomain, "")
        // Now we need to extract a potential call subdomain, which will be anything that's left in the domain
        //  at this point before the configured xmpp domain
        val subdomain = domain.subSequence(0, domain.indexOf(xmppEnvironment.xmppDomain))

        // Now just grab the call name
        val callName = startIq.room.localpart.toString()
        val callUrlInfo = CallUrlInfo("https://$domain/$subdomain/", callName)
        logger.info("Generated call info: $callUrlInfo")
        return when (startIq.recordingMode) {
            JibriIq.RecordingMode.FILE -> {
                jibriManager.startFileRecording(FileRecordingParams(
                        callParams = CallParams(
                            callUrlInfo = callUrlInfo,
                            callLoginParams = xmppEnvironment.callLogin
                        )
                ))
            }
            JibriIq.RecordingMode.STREAM -> {
                jibriManager.startStreaming(StreamingParams(
                    callParams = CallParams(
                        callUrlInfo = callUrlInfo,
                        callLoginParams = xmppEnvironment.callLogin
                    ),
                    youTubeStreamKey = startIq.streamId
                ))
            }
            else -> {
                StartServiceResult.ERROR
            }
        }
    }
}