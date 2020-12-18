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

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jitsi.jibri.JibriState
import org.jitsi.jibri.ProcessFailedToStart
import java.util.concurrent.CopyOnWriteArrayList

class WebhookClientTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()

    val client = WebhookClient(
        "test",
        client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request
                    with(request.url.toString()) {
                        when {
                            contains("success") -> {
                                respondOk()
                            }
                            contains("delay") -> {
                                delay(1000)
                                respondOk()
                            }
                            contains("error") -> {
                                respondError(HttpStatusCode.BadRequest)
                            }
                            else -> error("Unsupported URL")
                        }
                    }
                }
            }
        }
    )

    // Note: we should be able to use runBlockingTest in these tests and replace the 'eventually' calls with
    // calls to 'advanceUntilIdle', but there is a bug where things don't work because updateState launches
    // a coroutine on Dispatchers.IO.  See https://github.com/Kotlin/kotlinx.coroutines/pull/1206.
    // Also, it appears ktor does not play nicely with runBlockingTest (presumably it launches coroutines in
    // its own scopes), so that's a blocker as well.

    context("when the client") {
        context("has a valid subscriber") {
            client.addSubscriber("success")
            context("calling updateState") {
                runBlocking {
                    client.updateState(JibriState.Idle)
                }
                should("send a POST to the subscriber at the proper url") {
                    requests shouldHaveSize 1
                    with(requests[0]) {
                        url.toString() shouldContain "/v1/status"
                        method shouldBe HttpMethod.Post
                    }
                }
                should("send the correct data") {
                    requests[0].body.contentType shouldBe ContentType.Application.Json
                    with(requests[0].body) {
                        shouldBeInstanceOf<TextContent>()
                        // TODO: compare text
                    }
                }
                context("and calling updateStatus again") {
                    runBlocking {
                        client.updateState(JibriState.Error(ProcessFailedToStart("ffmpeg")))
                    }
                    should("send another request with the new status") {
                        requests shouldHaveSize 2
                        with(requests[1].body) {
                            shouldBeInstanceOf<TextContent>()
                            // TODO: compare text
                        }
                    }
                }
            }
        }
        context("has multiple subscribers") {
            client.addSubscriber("https://success")
            client.addSubscriber("https://delay")
            client.addSubscriber("https://error")
            context("calling updateStatus") {
                runBlocking {
                    client.updateState(JibriState.Idle)
                }
                should("send a POST to the subscribers at the proper url") {
                    requests shouldHaveSize 3
                    requests shouldContainRequestTo "success"
                    requests shouldContainRequestTo "delay"
                    requests shouldContainRequestTo "error"
                }
                context("and calling updateStatus again") {
                    requests.clear()
                    runBlocking {
                        client.updateState(JibriState.Idle)
                    }
                    should("send a POST to the subscribers at the proper url") {
                        requests shouldHaveSize 3
                        requests shouldContainRequestTo "success"
                        requests shouldContainRequestTo "delay"
                        requests shouldContainRequestTo "error"
                    }
                }
            }
        }
    }
})

infix fun List<HttpRequestData>.shouldContainRequestTo(host: String) {
    this.find { it.url.host.contains(host) } shouldNotBe null
}
