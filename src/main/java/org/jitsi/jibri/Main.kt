package org.jitsi.jibri

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.sourceforge.argparse4j.ArgumentParsers
import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import org.glassfish.jersey.jackson.*
import org.glassfish.jersey.server.*
import org.glassfish.jersey.servlet.*
import org.jitsi.jibri.api.rest.RestApi
import org.jitsi.jibri.api.xmpp.XmppApi
import org.jitsi.jibri.config.JibriConfig
import java.io.File
import java.io.FileNotFoundException
import javax.ws.rs.ext.ContextResolver

fun main(args: Array<String>) {
    val argParser = ArgumentParsers.newFor("Jibri").build()
            .defaultHelp(true)
            .description("Start Jibri")
    argParser.addArgument("-c", "--config")
            .required(true)
            .type(String::class.java)
            .help("Path to the jibri config file")

    val ns = argParser.parseArgs(args)
    val configFilePath = ns.getString("config")
    println("Using config file $configFilePath")

    val jibriConfigFile = File(configFilePath)
    if (!jibriConfigFile.exists()) {
        println("Error: Config file $configFilePath doesn't exist")
        System.exit(1)
    }

    val jibri = JibriManager(jibriConfigFile)
    val xmppApi = XmppApi(jibriManager = jibri, xmppConfigs = jibri.config.xmppEnvironments)
    Thread.sleep(Long.MAX_VALUE)
//    val jerseyConfig = ResourceConfig()
//    jerseyConfig.register(RestApi(jibri))
//            .register(ContextResolver<ObjectMapper> { ObjectMapper().registerModule(KotlinModule()) })
//            .register(JacksonFeature::class.java)
//
//    val servlet = ServletHolder(ServletContainer(jerseyConfig))
//
//    val server = Server(2222)
//    val context = ServletContextHandler(server, "/*")
//    context.addServlet(servlet, "/*")
//
//    try {
//        server.start()
//        server.join()
//    } catch (e: Exception) {
//        println("Error with server: ${e}")
//    } finally {
//        server.destroy()
//    }
}
