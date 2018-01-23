package org.jitsi.jibri

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import org.glassfish.jersey.jackson.*
import org.glassfish.jersey.server.*
import org.glassfish.jersey.servlet.*
import org.jitsi.jibri.api.rest.RestApi
import org.jitsi.jibri.config.JibriConfig
import java.io.File
import java.io.FileNotFoundException
import javax.ws.rs.ext.ContextResolver

fun main(args: Array<String>) {
    //TODO: change this to a program arg
    val configFilePath = "/Users/bbaldino/jitsi/jibri-new/config.json"
    val jibriConfig = try {
        jacksonObjectMapper().readValue<JibriConfig>(File(configFilePath))
    } catch (e: FileNotFoundException) {
        println("Unable to read config file ${configFilePath}")
        return
    }
    val jibri = JibriManager(jibriConfig)
    val jerseyConfig = ResourceConfig()
    jerseyConfig.register(RestApi(jibri))
            .register(ContextResolver<ObjectMapper> { ObjectMapper().registerModule(KotlinModule()) })
            .register(JacksonFeature::class.java)

    val servlet = ServletHolder(ServletContainer(jerseyConfig))

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
}
