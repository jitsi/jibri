package org.jitsi.jibri.api.http.internal

import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.util.debug
import java.util.logging.Logger
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Response

@Path("/jibri/api/internal/v1.0")
class InternalHttpApi(private val jibriManager: JibriManager) {
    private val logger = Logger.getLogger(this::class.simpleName)
    /**
     * Signal this Jibri to reload its config file at the soonest opportunity
     * (when it does not have a currently running service)
     */
    @POST
    @Path("reloadConfig")
    fun reloadConfig(): Response {
        logger.debug("Got reload config reset")
        jibriManager.reloadConfig()
        return Response.ok().build()
    }
}