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

package org.jitsi.jibri.api.http

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.ServiceParams
import org.jitsi.jibri.StartServiceResult
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.util.extensions.debug
import java.util.logging.Logger
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// TODO: this needs to include usageTimeout
data class StartServiceParams(
    val callParams: CallParams,
    /**
     * XMPP login information to be used if [RecordingSinkType] is
     * [RecordingSinkType.FILE] or [RecordingSinkType.STREAM] in order
     * to make the recorder 'invisible'
     */
    val callLoginParams: XmppCredentials? = null,
    val sinkType: RecordingSinkType,
    val youTubeStreamKey: String? = null,
    /**
     * Params to be used if [RecordingSinkType] is [RecordingSinkType.GATEWAY]
     */
    val sipClientParams: SipClientParams? = null
)

/**
 * The [HttpApi] is for starting and stopping the various Jibri services via the
 * [JibriManager], as well as retrieving the health and status of this Jibri
 */
@Path("/jibri/api/v1.0")
class HttpApi(private val jibriManager: JibriManager) {
    private val logger = Logger.getLogger(this::class.qualifiedName)

    /**
     * Get the health of this Jibri in the format of a json-encoded
     * [org.jitsi.jibri.health.JibriHealth] object
     */
    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Response {
        logger.debug("Got health request")
        return Response.ok(jibriManager.healthCheck()).build()
    }

    /**
     * [startService] will start a new service using the given [StartServiceParams].
     * Returns a response with [Response.Status.OK] on success, [Response.Status.PRECONDITION_FAILED]
     * if this Jibri is already busy and [Response.Status.INTERNAL_SERVER_ERROR] on error
     */
    @POST
    @Path("startService")
    @Consumes(MediaType.APPLICATION_JSON)
    fun startService(startServiceParams: StartServiceParams): Response {
        logger.debug("Got a start service request with params $startServiceParams")
        val result = when (startServiceParams.sinkType) {
            RecordingSinkType.FILE -> run {
                // If it's a file recording, it must have the callLoginParams set
                val callLoginParams = startServiceParams.callLoginParams ?: return@run StartServiceResult.ERROR
                jibriManager.startFileRecording(
                    ServiceParams(usageTimeoutMinutes = 0),
                    FileRecordingRequestParams(startServiceParams.callParams, callLoginParams),
                    environmentContext = null
                )
            }
            RecordingSinkType.STREAM -> run {
                val youTubeStreamKey = startServiceParams.youTubeStreamKey ?: return@run StartServiceResult.ERROR
                // If it's a stream, it must have the callLoginParams set
                val callLoginParams = startServiceParams.callLoginParams ?: return@run StartServiceResult.ERROR
                jibriManager.startStreaming(
                    ServiceParams(usageTimeoutMinutes = 0),
                    StreamingParams(startServiceParams.callParams, callLoginParams, youTubeStreamKey),
                    environmentContext = null
                )
            }
            RecordingSinkType.GATEWAY -> run {
                // If it's a sip gateway, it must have sipClientParams set
                val sipClientParams = startServiceParams.sipClientParams ?: return@run StartServiceResult.ERROR
                jibriManager.startSipGateway(
                    ServiceParams(usageTimeoutMinutes = 0),
                    SipGatewayServiceParams(
                        startServiceParams.callParams,
                        sipClientParams)
                    )
            }
        }
        return when (result) {
            StartServiceResult.SUCCESS -> Response.ok().build()
            StartServiceResult.BUSY -> Response.status(Response.Status.PRECONDITION_FAILED).build()
            StartServiceResult.ERROR -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * [stopService] will stop the current service immediately
     */
    @POST
    @Path("stopService")
    fun stopService(): Response {
        logger.debug("Got stop service request")
        jibriManager.stopService()
        return Response.ok().build()
    }
}
