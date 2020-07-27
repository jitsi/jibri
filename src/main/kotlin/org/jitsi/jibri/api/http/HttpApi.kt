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

package org.jitsi.jibri.api.http

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.JibriBusyException
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.metaconfig.config
import java.util.logging.Logger
import javax.ws.rs.core.Response

// TODO: this needs to include usageTimeout
data class StartServiceParams(
    val sessionId: String,
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

class HttpApi(
    private val jibriManager: JibriManager,
    private val jibriStatusManager: JibriStatusManager
) {
    private val logger = Logger.getLogger(this::class.qualifiedName)

    fun Application.apiModule() {
        install(ContentNegotiation) {
            jackson {}
        }

        routing {
            route("jibri/api/v1.0") {
                /**
                 * Get the health of this Jibri in the format of a json-encoded
                 * [JibriHealth] object
                 */
                get("health") {
                    logger.debug("Got health request")
                    val health = JibriHealth(jibriStatusManager.overallStatus, jibriManager.currentEnvironmentContext)
                    logger.debug("Returning health $health")
                    call.respond(health)
                }

                /**
                 * Start a new service using the given [StartServiceParams].
                 * Returns a response with [Response.Status.OK] on success, [Response.Status.PRECONDITION_FAILED]
                 * if this Jibri is already busy or params were missing and [Response.Status.INTERNAL_SERVER_ERROR] on
                 * error
                 * NOTE: start service is largely async, so a return of [Response.Status.OK] here just means Jibri
                 * was able to *try* to start the request.  We don't have a way to get ongoing updates about services
                 * via the HTTP API at this point.
                 */
                post("startService") {
                    val startServiceParams = call.receive<StartServiceParams>()
                    logger.debug("Got a start service request with params $startServiceParams")
                    try {
                        handleStartService(startServiceParams)
                        call.respond(HttpStatusCode.OK)
                    } catch (e: JibriBusyException) {
                        call.respond(HttpStatusCode.PreconditionFailed, "Jibri is currently busy")
                    } catch (e: IllegalStateException) {
                        call.respond(HttpStatusCode.PreconditionFailed, e.message ?: "")
                    } catch (t: Throwable) {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                /**
                 * [stopService] will stop the current service immediately
                 */
                post("stopService") {
                    logger.debug("Got stop service request")
                    jibriManager.stopService()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    private fun handleStartService(startServiceParams: StartServiceParams) {
        when (startServiceParams.sinkType) {
            RecordingSinkType.FILE -> {
                // If it's a file recording, it must have the callLoginParams set
                val callLoginParams = startServiceParams.callLoginParams
                    ?: throw IllegalStateException("Call login params missing")
                jibriManager.startFileRecording(
                    ServiceParams(usageTimeoutMinutes = 0),
                    FileRecordingRequestParams(
                        startServiceParams.callParams,
                        startServiceParams.sessionId,
                        callLoginParams
                    ),
                    environmentContext = null
                )
            }
            RecordingSinkType.STREAM -> {
                val youTubeStreamKey = startServiceParams.youTubeStreamKey
                    ?: throw IllegalStateException("Stream key missing")
                // If it's a stream, it must have the callLoginParams set
                val callLoginParams = startServiceParams.callLoginParams
                    ?: throw IllegalStateException("Call login params missing")
                jibriManager.startStreaming(
                    ServiceParams(usageTimeoutMinutes = 0),
                    StreamingParams(
                        startServiceParams.callParams,
                        startServiceParams.sessionId,
                        callLoginParams,
                        youTubeStreamKey
                    ),
                    environmentContext = null
                )
            }
            RecordingSinkType.GATEWAY -> {
                // If it's a sip gateway, it must have sipClientParams set
                val sipClientParams = startServiceParams.sipClientParams
                    ?: throw IllegalStateException("SIP client params missing")
                jibriManager.startSipGateway(
                    ServiceParams(usageTimeoutMinutes = 0),
                    // TODO: add session ID
                    SipGatewayServiceParams(
                        startServiceParams.callParams,
                        startServiceParams.callLoginParams,
                        sipClientParams)
                )
            }
        }
    }

    companion object {
        val port: Int by config {
            "http_api_port".from(Config.commandLineArgs).softDeprecated("use jibri.api.http.external-api-port")
            "jibri.api.http.external-api-port".from(Config.configSource)
        }
    }
}
