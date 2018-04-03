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

package org.jitsi.jibri.api.http.internal

import org.jitsi.jibri.util.extensions.schedule
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Logger
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Response

@Path("/jibri/api/internal/v1.0")
class InternalHttpApi(
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val configChangedHandler: () -> Unit,
    private val shutdownHandler: () -> Unit
) {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * Signal this Jibri to reload its config file at the soonest opportunity
     * (when it does not have a currently running service). Returns a 200
     * and schedules a shutdown for when it becomes idle.
     */
    @POST
    @Path("notifyConfigChanged")
    fun reloadConfig(): Response {
        logger.info("Config file changed")
        // Schedule firing the handler so we have a chance to send the successful
        // response.
        executor.schedule(1) {
            configChangedHandler()
        }
        return Response.ok().build()
    }

    /**
     * Signal this Jibri to (cleanly) stop any services that are
     * running and shutdown.  Returns a 200 and schedules a shutdown with a 1
     * second delay.
     */
    @POST
    @Path("shutdown")
    fun shutdown(): Response {
        logger.info("Shutting down")
        // Schedule firing the handler so we have a chance to send the successful
        // response.
        executor.schedule(1) {
            shutdownHandler()
        }
        return Response.ok().build()
    }
}
