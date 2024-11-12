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

package org.jitsi.jibri.webhooks.v1

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.status.JibriSessionStatus
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jwt.JwtInfo
import org.jitsi.jwt.RefreshingJwt
import org.jitsi.metaconfig.optionalconfig
import org.jitsi.utils.logging2.createLogger
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A client for notifying subscribers of Jibri events
 */
class WebhookClient(
    private val jibriId: String,
    client: HttpClient = HttpClient(Apache)
) {
    private val logger = createLogger()
    private val webhookSubscribers: MutableSet<String> = CopyOnWriteArraySet()
    private val jwtInfo: JwtInfo? by optionalconfig {
        "jibri.jwt-info".from(Config.configSource)
            .convertFrom(JwtInfo.Companion::fromConfig)
    }
    private val jwt: String? by RefreshingJwt(jwtInfo)

    private val client = client.config {
        expectSuccess = false
        install(ContentNegotiation) {
            jackson {}
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 2000
        }
        jwt?.let {
            defaultRequest {
                bearerAuth(it)
            }
        }
    }

    fun addSubscriber(subscriberBaseUrl: String) {
        webhookSubscribers.add(subscriberBaseUrl)
    }

    fun removeSubscriber(subscriberBaseUrl: String) {
        webhookSubscribers.remove(subscriberBaseUrl)
    }

    fun updateStatus(status: JibriStatus) = runBlocking {
        logger.debug { "Updating ${webhookSubscribers.size} subscribers of status" }
        webhookSubscribers.forEach { subscriberBaseUrl ->
            launch(TaskPools.ioPool.asCoroutineDispatcher()) {
                logger.debug { "Sending request to $subscriberBaseUrl" }
                try {
                    val resp = client.postJson("$subscriberBaseUrl/v1/status") {
                        setBody(JibriEvent.HealthEvent(jibriId, status))
                    }
                    logger.debug { "Got response from $subscriberBaseUrl: $resp" }
                    if (resp.status != HttpStatusCode.OK) {
                        logger.error("Error updating health for webhook subscriber $subscriberBaseUrl: $resp")
                    }
                } catch (e: HttpRequestTimeoutException) {
                    logger.error("Request to $subscriberBaseUrl timed out")
                }
            }
        }
    }

    fun updateSessionStatus(session: JibriSessionStatus) = runBlocking {
        logger.debug { "Updating ${webhookSubscribers.size} subscribers of the session status" }
        webhookSubscribers.forEach { subscriberBaseUrl ->
            launch(TaskPools.ioPool.asCoroutineDispatcher()) {
                logger.debug { "Sending request to $subscriberBaseUrl" }
                try {
                    val resp = client.postJson("$subscriberBaseUrl/v1/session/status") {
                        setBody(JibriEvent.SessionEvent(jibriId, session))
                    }
                    logger.debug { "Got response from $subscriberBaseUrl: $resp" }
                    if (resp.status != HttpStatusCode.OK) {
                        logger.error("Error updating session for webhook subscriber $subscriberBaseUrl: $resp")
                    }
                } catch (e: HttpRequestTimeoutException) {
                    logger.error("Request to $subscriberBaseUrl timed out")
                }
            }
        }
    }
}

/**
 * Just like [HttpClient.post], but automatically sets the content type to
 * [ContentType.Application.Json].
 */
private suspend inline fun HttpClient.postJson(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post(urlString) {
    block()
    contentType(ContentType.Application.Json)
}
