package org.jitsi.jibri.api.http.internal

import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.util.debug
import java.util.logging.Logger
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Response

@Path("/jibri/api/internal/v1.0")
class InternalHttpApi(private val configChangedHandler: () -> Unit) {
    private val logger = Logger.getLogger(this::class.simpleName)
    /**
     * Signal this Jibri to reload its config file at the soonest opportunity
     * (when it does not have a currently running service)
     */
    @POST
    @Path("notifyConfigChanged")
    fun reloadConfig(): Response {
        logger.debug("Config file changed")
        configChangedHandler()
        // If Jibri is currently idle, then we'll exit in the context
        // of the above call, so this response will never get sent.
        // Since the old Jibri used a signal (which has no concept of
        // a response), I'm assuming it will be ok if we don't return one
        // in some instances here.
        return Response.ok().build()
    }
}