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

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseHeaderValue
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.JibriBusyException
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.JibriHealth
import org.jitsi.jibri.metrics.JibriMetricsContainer
import org.jitsi.jibri.metrics.StatsConfig
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.JibriFailure
import org.jitsi.jibri.status.JibriSessionStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.webhooks.v1.WebhookClient
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.createLogger
import org.jitsi.xmpp.extensions.jibri.JibriIq

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
    private val jibriStatusManager: JibriStatusManager,
    private val webhookClient: WebhookClient
) {
    private val logger = createLogger()

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
                    logger.debug { "Got health request" }
                    val health = JibriHealth(jibriStatusManager.overallStatus, jibriManager.currentEnvironmentContext)
                    logger.debug { "Returning health $health" }
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
                    try {
                        val startServiceParams = call.receive<StartServiceParams>()
                        logger.debug { "Got a start service request with params $startServiceParams" }

                        val serviceStatusHandler = createServiceStatusHandler(startServiceParams, webhookClient)
                        handleStartService(startServiceParams, serviceStatusHandler)
                        call.respond(HttpStatusCode.OK)
                    } catch (e: JibriBusyException) {
                        call.respond(HttpStatusCode.PreconditionFailed, "Jibri is currently busy")
                    } catch (e: IllegalStateException) {
                        call.respond(HttpStatusCode.PreconditionFailed, e.message ?: "")
                    } catch (t: Throwable) {
                        logger.error("Error starting service $t", t)
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                /**
                 * [stopService] will stop the current service immediately
                 */
                post("stopService") {
                    logger.debug { "Got stop service request" }
                    jibriManager.stopService()
                    call.respond(HttpStatusCode.OK)
                }
            }
            if (StatsConfig.enablePrometheus) {
                logger.info("Enabling prometheus interface at :$port/metrics")
                get("/metrics") {
                    val accepts =
                        parseHeaderValue(call.request.headers["Accept"]).sortedByDescending { it.quality }
                            .map { it.value }
                    val (metrics, contentType) = JibriMetricsContainer.getMetrics(accepts)
                    call.respondText(metrics, contentType = ContentType.parse(contentType))
                }
            }
        }
    }

    private fun createServiceStatusHandler(
        serviceParams: StartServiceParams,
        webhookClient: WebhookClient
    ): JibriServiceStatusHandler {
        return { serviceState ->
            when (serviceState) {
                is ComponentState.Error -> {
                    val failure = JibriFailure(
                        JibriIq.FailureReason.ERROR,
                        serviceState.error
                    )
                    val componentSessionStatus = JibriSessionStatus(
                        serviceParams.sessionId,
                        JibriIq.Status.OFF,
                        serviceParams.sipClientParams?.sipAddress,
                        failure,
                        serviceState.error.shouldRetry()
                    )
                    logger.info(
                        "Current service had an error ${serviceState.error}, " +
                            "sending status error $componentSessionStatus"
                    )
                    webhookClient.updateSessionStatus(componentSessionStatus)
                }
                is ComponentState.Finished -> {
                    val componentSessionStatus = JibriSessionStatus(
                        serviceParams.sessionId,
                        JibriIq.Status.OFF,
                        serviceParams.sipClientParams?.sipAddress
                    )
                    logger.info("Current service finished, sending status off $componentSessionStatus")
                    webhookClient.updateSessionStatus(componentSessionStatus)
                }
                is ComponentState.Running -> {
                    val componentSessionStatus = JibriSessionStatus(
                        serviceParams.sessionId,
                        JibriIq.Status.ON,
                        serviceParams.sipClientParams?.sipAddress
                    )
                    logger.info("Current service started up successfully, sending status on $componentSessionStatus")
                    webhookClient.updateSessionStatus(componentSessionStatus)
                }
                else -> {
                    logger.info("Webhook client ignoring service state update: $serviceState")
                }
            }
        }
    }

    private fun handleStartService(startServiceParams: StartServiceParams, statusHandler: JibriServiceStatusHandler) {
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
                    environmentContext = null,
                    statusHandler
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
                    environmentContext = null,
                    statusHandler
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
                        sipClientParams
                    ),
                    environmentContext = null,
                    statusHandler
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
