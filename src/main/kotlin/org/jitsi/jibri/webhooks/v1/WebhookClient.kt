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
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import java.util.concurrent.CopyOnWriteArraySet
import java.util.logging.Logger

/**
 * A client for notifying subscribers of Jibri events
 */
class WebhookClient private constructor(
    private val jibriId: String,
    private val client: HttpClient
) {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val webhookSubscribers: MutableSet<String> = CopyOnWriteArraySet()

    fun addSubscriber(subscriberBaseUrl: String) {
        webhookSubscribers.add(subscriberBaseUrl)
    }

    fun removeSubscriber(subscriberBaseUrl: String) {
        webhookSubscribers.remove(subscriberBaseUrl)
    }

    fun updateStatus(status: JibriStatus) = runBlocking {
        webhookSubscribers.forEach { subscriberBaseUrl ->
            launch {
                logger.debug("Sending request to $subscriberBaseUrl")
                val resp = client.postJson<HttpResponse>("$subscriberBaseUrl/v1/health") {
                    body = JibriEvent.HealthEvent(jibriId, status)
                }
                logger.debug("Got response from $subscriberBaseUrl: $resp")
                if (resp.status != HttpStatusCode.OK) {
                    logger.error("Error updating health for webhook subscriber $subscriberBaseUrl: $resp")
                }
            }
        }
    }

    /**
     * To make the client testable, we use this helper function to enable both
     * the client being able to always add the config it needs (installing
     * [JsonFeature]), but also letting the caller add its own config (which is
     * necessary when using a mock client engine).
     */
    companion object {
        operator fun <T : HttpClientEngineConfig> invoke(
            jibriId: String,
            engineFactory: HttpClientEngineFactory<T>,
            block: HttpClientConfig<T>.() -> Unit = {}
        ): WebhookClient {
            val client = HttpClient(engineFactory) {
                block()
                install(JsonFeature) {
                    serializer = JacksonSerializer()
                }
            }
            return WebhookClient(jibriId, client)
        }
    }
}

/**
 * Just like [HttpClient.post], but automatically sets the content type to
 * [ContentType.Application.Json].
 */
private suspend inline fun <reified T> HttpClient.postJson(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(urlString) {
    block()
    contentType(ContentType.Application.Json)
}