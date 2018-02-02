package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIqProvider
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriStatusPacketExt
import org.jitsi.jibri.*
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.util.error
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.PresenceListener
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.EntityBareJid
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
    private val mucManager = MucClientManager()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        JibriStatusPacketExt.registerExtensionProvider()
        ProviderManager.addIQProvider(
            JibriIq.ELEMENT_NAME, JibriIq.NAMESPACE, JibriIqProvider()
        )
        // We don't care about presence, but we do want to listen for any incoming IQ messages
        mucManager.setIqRequestHandler(object: JibriSyncIqRequestHandler() {
            override fun handleJibriIqRequest(jibriIq: JibriIq): IQ {
                logger.info("Received JibriIq")
                when (jibriIq.action) {
                    JibriIq.Action.START -> {
                        logger.info("Received start request")
                        // Immediately respond that the request is pending
                        //TODO: is there any good way to create a custom iq result?
                        val initialResponse = JibriIqHelper.createResult(jibriIq)
                        initialResponse.status = JibriIq.Status.PENDING
                        // Start the actual service and send an IQ once we get the result
                        executor.submit {
                            val resultIq = JibriIq()
                            resultIq.to = jibriIq.from
                            resultIq.type = IQ.Type.set
                            logger.info("Starting service")
                            resultIq.status = when (handleStartService(jibriIq)) {
                                StartServiceResult.SUCCESS -> JibriIq.Status.ON
                                StartServiceResult.BUSY -> JibriIq.Status.BUSY
                                StartServiceResult.ERROR -> JibriIq.Status.FAILED
                            }
                            logger.info("Sending started response")
                            mucManager.sendStanza(resultIq)
                        }
                        logger.info("Sending start pending response")
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
        })

        // Join all the mucs we've been told to
        for (config in xmppConfigs) {
            for (host in config.xmppServerHosts) {
                logger.info("Connecting to xmpp environment on $host with config $config")
                logger.info("joining muc ${config.controlMuc.roomName}@${config.controlMuc.domain}")
                logger.info("joining muc ${JidCreate.entityBareFrom(config.controlMuc.roomName + "@" + config.controlMuc.domain)}")
                if (mucManager.joinMuc(
                        XMPPTCPConnectionConfiguration.builder()
                                .setHost(host)
                                .setXmppDomain(config.controlLogin.domain)
                                .setUsernameAndPassword(config.controlLogin.username, config.controlLogin.password)
                                .build(),
                        JidCreate.entityBareFrom("${config.controlMuc.roomName}@${config.controlMuc.domain}"),
                        Resourcepart.from(config.controlMuc.nickname)
                )) {
                    if (!jibriManager.busy()) {
                        val jibriStatus = JibriStatusPacketExt()
                        jibriStatus.status = JibriStatusPacketExt.Status.IDLE
                        val pres = Presence(Presence.Type.available)
                        pres.to = JidCreate.bareFrom("${config.controlMuc.roomName}@${config.controlMuc.domain}")
                        pres.overrideExtension(jibriStatus)
                        mucManager.sendStanza(pres)
                        //mucManager.setPresenceExtension(jibriStatus)

                    }
                } else {
                    logger.error("Error joining muc")
                }
            }
        }
    }

    private fun handleStartService(startIq: JibriIq): StartServiceResult {
        val jibriDisplayName = startIq.displayName
        /**
         * The call url is constructed from the xmpp domain, an optional subdomain, and a callname like so:
         * https://domain/subdomain/callName
         * The domain is pulled from the xmpp domain of the connection sending the request
         */
        val domain = startIq.room.domain.toString()
        val callName = startIq.room.localpart.toString()
        //TODO: i think more work is needed to get the actual call url, but gonna start with this and tweak
        // to see what breaks
        val callUrlInfo = CallUrlInfo("https://${domain.replace("conference.", "")}", callName)
        return when (startIq.recordingMode) {
            JibriIq.RecordingMode.FILE -> {
                jibriManager.startFileRecording(FileRecordingParams(
                        callUrlInfo = callUrlInfo
                ))
            }
            JibriIq.RecordingMode.STREAM -> {
                jibriManager.startStreaming(StreamingParams(
                        callUrlInfo = callUrlInfo,
                        youTubeStreamKey = startIq.streamId
                ))
            }
            else -> {
                StartServiceResult.ERROR
            }
        }
    }
}