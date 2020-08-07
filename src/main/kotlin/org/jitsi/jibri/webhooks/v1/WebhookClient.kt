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

import com.typesafe.config.ConfigObject
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.HttpRequestTimeoutException
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.util.RefreshingProperty
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.metaconfig.optionalconfig
import java.io.FileReader
import java.security.PrivateKey
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.concurrent.CopyOnWriteArraySet
import java.util.logging.Logger

/**
 * A client for notifying subscribers of Jibri events
 */
class WebhookClient(
    private val jibriId: String,
    private val clock: Clock = Clock.systemUTC(),
    client: HttpClient = HttpClient(Apache)
) {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val webhookSubscribers: MutableSet<String> = CopyOnWriteArraySet()
    private val jwtInfo: JwtInfo? by optionalconfig {
        "jibri.jwt-info".from(Config.configSource)
            .convertFrom<ConfigObject>(JwtInfo.Companion::fromConfig)
    }

    // We refresh 5 minutes before the expiration
    private val jwt: String? by RefreshingProperty(jwtInfo?.ttl?.minus(Duration.ofMinutes(5)) ?: INFINITE) {
        jwtInfo?.let {
            Jwts.builder()
                .setHeaderParam("kid", it.kid)
                .setIssuer(it.issuer)
                .setAudience(it.audience)
                .setExpiration(Date.from(clock.instant().plus(it.ttl)))
                .signWith(SignatureAlgorithm.RS256, it.privateKey)
                .compact()
        }
    }

    private val client = client.config {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 2000
        }
        jwt?.let {
            defaultRequest {
                header("Authorization", "Bearer $jwt")
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
        logger.debug("Updating ${webhookSubscribers.size} subscribers of status")
        webhookSubscribers.forEach { subscriberBaseUrl ->
            launch(TaskPools.ioPool.asCoroutineDispatcher()) {
                logger.debug("Sending request to $subscriberBaseUrl")
                try {
                    val resp = client.postJson<HttpResponse>("$subscriberBaseUrl/v1/status") {
                        body = JibriEvent.HealthEvent(jibriId, status)
                    }
                    logger.debug("Got response from $subscriberBaseUrl: $resp")
                    if (resp.status != HttpStatusCode.OK) {
                        logger.error("Error updating health for webhook subscriber $subscriberBaseUrl: $resp")
                    }
                } catch (e: HttpRequestTimeoutException) {
                    logger.error("Request to $subscriberBaseUrl timed out")
                }
            }
        }
    }
}

private val INFINITE = Duration.ofSeconds(Long.MAX_VALUE)

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

private data class JwtInfo(
    val privateKey: PrivateKey,
    val kid: String,
    val issuer: String,
    val audience: String,
    val ttl: Duration
) {
    companion object {
        private val logger = Logger.getLogger(this::class.qualifiedName)
        fun fromConfig(jwtConfigObj: ConfigObject): JwtInfo {
            // Any missing or incorrect value here will throw, which is what we want:
            // If anything is wrong, we should fail to create the JwtInfo
            val jwtConfig = jwtConfigObj.toConfig()
            logger.info("got jwtConfig: ${jwtConfig.root().render()}")
            try {
                return JwtInfo(
                    privateKey = parseKeyFile(jwtConfig.getString("signing-key-path")),
                    kid = jwtConfig.getString("kid"),
                    issuer = jwtConfig.getString("issuer"),
                    audience = jwtConfig.getString("audience"),
                    ttl = jwtConfig.getDuration("ttl").withMinimum(Duration.ofMinutes(10))
                )
            } catch (t: Throwable) {
                logger.info("Unable to create JwtInfo: $t")
                throw t
            }
        }
    }
}

private fun parseKeyFile(keyFilePath: String): PrivateKey {
    val parser = PEMParser(FileReader(keyFilePath))
    return (parser.readObject() as PEMKeyPair).let { pemKeyPair ->
        JcaPEMKeyConverter().getKeyPair(pemKeyPair).private
    }
}

/**
 * Returns [min] if this Duration is less than that minimum, otherwise this
 */
private fun Duration.withMinimum(min: Duration): Duration = maxOf(this, min)
