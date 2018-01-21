package org.jitsi.jibri

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import org.glassfish.jersey.jackson.*
import org.glassfish.jersey.server.*
import org.glassfish.jersey.servlet.*
import org.jitsi.jibri.api.rest.RestApi
import javax.ws.rs.ext.ContextResolver

fun main(args: Array<String>) {
    val jibri = Jibri()
    val config = ResourceConfig()
    config.register(RestApi(jibri))
            .register(ContextResolver<ObjectMapper> { ObjectMapper().registerModule(KotlinModule()) })
            .register(JacksonFeature::class.java)

    val servlet = ServletHolder(ServletContainer(config))

    val server = Server(2222)
    val context = ServletContextHandler(server, "/*")
    context.addServlet(servlet, "/*")

    try {
        server.start()
        server.join()
    } catch (e: Exception) {

    } finally {
        server.destroy()
    }

    Thread.sleep(Long.MAX_VALUE)
}
