package org.jitsi.jibri.api.xmpp

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jitsi.jibri.*
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.parts.Resourcepart
import java.util.concurrent.Executors

data class XmppMucInfo(
        val xmppConnectionConfig: XMPPTCPConnectionConfiguration,
        val mucJid: EntityBareJid,
        val mucNickname: Resourcepart
)

/**
 * [XmppApi] connects to an XMPP MUC according to the given [xmppConfig] and listens
 * for IQ messages which contain Jibri commands, which it relays
 * to the given [jibriManager].  It takes care of translating the XMPP commands into the appropriate
 * [JibriManager] API calls and translates the results into XMPP IQ responses.
 */
class XmppApi(
        private val jibriManager: JibriManager,
        xmppConfigs: List<XmppMucInfo>
) {
    private val mucManager = MucClientManager()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        // We don't care about presence, but we do want to listen for any incoming IQ messages
        mucManager.setIqRequestHandler(object: JibriSyncIqRequestHandler() {
            override fun handleJibriIqRequest(jibriIq: JibriIq): IQ {
                when (jibriIq.action) {
                    JibriIq.Action.START -> {
                        // Immediately respond that the request is pending
                        val initialResponse = IQ.createResultIQ(jibriIq) as JibriIq
                        initialResponse.status = JibriIq.Status.PENDING
                        // Start the actual service and send an IQ once we get the result
                        executor.submit {
                            val resultIq = JibriIq()
                            resultIq.to = jibriIq.from
                            resultIq.from = jibriIq.to
                            resultIq.status = when (handleStartService(jibriIq)) {
                                StartServiceResult.SUCCESS -> JibriIq.Status.ON
                                StartServiceResult.BUSY -> JibriIq.Status.BUSY
                                StartServiceResult.ERROR -> JibriIq.Status.FAILED
                            }
                            mucManager.sendStanza(resultIq)
                        }
                        return initialResponse
                    }
                    JibriIq.Action.STOP -> {
                        jibriManager.stopService()
                        // By this point the service has been fully stopped
                        val response = IQ.createResultIQ(jibriIq) as JibriIq
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
            mucManager.joinMuc(config.xmppConnectionConfig, config.mucJid, config.mucNickname)
        }
    }

    private fun handleStartService(startIq: JibriIq): StartServiceResult {
        val jibriDisplayName = startIq.displayName
        /**
         * The call url is constructed from the xmpp domain, an optional subdomain, and a callname like so:
         * https://domain/subdomain/callName
         * The domain is pulled from the xmpp domain of the connection sending the request
         */
        //TODO: is this correct? must the room jid's domain match the xmpp match the domain that will have the
        // call url?
        val domain = startIq.room.domain
        val callName = startIq.room.localpart.toString()
        //TODO: i think more work is needed to get the actual call url, but gonna start with this and tweak
        // to see what breaks
        val callUrlInfo = CallUrlInfo("https://$domain", callName)
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