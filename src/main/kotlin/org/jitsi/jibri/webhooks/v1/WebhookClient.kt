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

class WebhookClient() {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    private val webhookSubscribers = mutableListOf<String>()

    fun updateStatus(status: JibriStatus) = runBlocking {
        webhookSubscribers.forEach { subscriberBaseUrl ->
            launch {
                val resp = client.postJson<HttpResponse>("$subscriberBaseUrl/health")
                if (resp.status != HttpStatusCode.OK) {
                    println("Error updating webhook subscriber $subscriberBaseUrl: $resp")
                }
            }
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
): T = post {
    post<T>(urlString) {
        block()
        contentType(ContentType.Application.Json)
    }
}