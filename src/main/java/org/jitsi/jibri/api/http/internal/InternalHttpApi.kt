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

import java.util.logging.Logger
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Response

@Path("/jibri/api/internal/v1.0")
class InternalHttpApi(
    private val configChangedHandler: () -> Unit,
    private val shutdownHandler: () -> Unit) {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * Signal this Jibri to reload its config file at the soonest opportunity
     * (when it does not have a currently running service).  If Jibri is
     * currently idle, it will shutdown immediately and return no HTTP response.
     * If it is not idle, it will return a 200 and schedule a shutdown
     * for when it becomes idle.
     */
    @POST
    @Path("notifyConfigChanged")
    fun reloadConfig(): Response {
        logger.info("Config file changed")
        configChangedHandler()
        // If Jibri is currently idle, then we'll exit in the context
        // of the above call, so this response will never get sent.
        // Since the old Jibri used a signal (which has no concept of
        // a response), I'm assuming it will be ok if we don't return one
        // in some instances here.
        return Response.ok().build()
    }

    /**
     * Signal this Jibri to (cleanly) stop any services that are
     * running and shutdown.  Will not send an HTTP response, since
     * it will shutdown immediately.
     */
    @POST
    @Path("shutdown")
    fun shutdown() {
        logger.info("Shutting down")
        shutdownHandler()
    }
}
