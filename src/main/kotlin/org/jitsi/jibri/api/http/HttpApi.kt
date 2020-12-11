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
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.toLegacyJibriHealth
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.utils.logging2.createLogger

class HttpApi(private val jibriManager: JibriManager) {
    private val logger = createLogger()

    fun Application.apiModule() {
        install(ContentNegotiation) {
            jackson {}
        }

        routing {
            route("jibri/api/v1.0") {
                /**
                 * Get the health of this Jibri in the format of a json-encoded
                 * [org.jitsi.jibri.JibriState] object
                 */
                get("health") {
                    logger.debug("Got health request")
                    call.respond(jibriManager.currentState.value.toLegacyJibriHealth())
                }
            }
        }
    }

    companion object {
        val port: Int by config("jibri.api.http.external-api-port".from(Config.configSource))
    }
}
