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

package org.jitsi.jibri.api.http.internal

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jitsi.jibri.config.Config
import org.jitsi.metaconfig.config
import java.util.logging.Logger

class InternalHttpApi(
    private val configChangedHandler: () -> Unit,
    private val gracefulShutdownHandler: () -> Unit,
    private val shutdownHandler: () -> Unit
) {
    private val logger = Logger.getLogger(this::class.qualifiedName)

    fun Application.internalApiModule() {
        install(ContentNegotiation) {
            jackson {}
        }

        routing {
            route("/jibri/api/internal/v1.0") {
                /**
                 * Signal this Jibri to shutdown gracefully, meaning shut down when
                 * it is idle (i.e. finish any currently running service). Returns a 200
                 * and schedules a shutdown for when it becomes idle.
                 */
                post("gracefulShutdown") {
                    logger.info("Jibri gracefully shutting down")
                    respondOkAndRun(gracefulShutdownHandler)
                }
                /**
                 * Signal this Jibri to reload its config file at the soonest opportunity
                 * (when it does not have a currently running service). Returns a 200.
                 */
                post("notifyConfigChanged") {
                    logger.info("Config file changed")
                    respondOkAndRun(configChangedHandler)
                }
                /**
                 * Signal this Jibri to (cleanly) stop any services that are
                 * running and shutdown.  Returns a 200.
                 */
                post("shutdown") {
                    logger.info("Jibri is forcefully shutting down")
                    respondOkAndRun(shutdownHandler)
                }
            }
        }
    }
    companion object {
        val port: Int by config {
            "internal_http_port"
                .from(Config.commandLineArgs).softDeprecated("use jibri.api.http.internal-api-port")
            "jibri.api.http.internal-api-port".from(Config.configSource)
        }
    }
}

/**
 * Responds with [HttpStatusCode.OK] and then runs the given block
 */
private suspend fun PipelineContext<*, ApplicationCall>.respondOkAndRun(block: () -> Unit) {
    val latch = CompletableDeferred<Nothing>()
    coroutineScope {
        launch {
            latch.join()
            block()
        }
        try {
            call.respond(HttpStatusCode.OK)
        } finally {
            latch.cancel()
        }
    }
}
