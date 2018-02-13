package org.jitsi.jibri

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.sourceforge.argparse4j.ArgumentParsers
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.jackson.JacksonFeature
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.jitsi.jibri.api.http.HttpApi
import org.jitsi.jibri.api.http.internal.InternalHttpApi
import org.jitsi.jibri.api.xmpp.XmppApi
import java.io.File
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
    // InternalHttpApi
    val internalApiThread = Thread {
        val jerseyConfig = ResourceConfig()
        jerseyConfig.register(InternalHttpApi(jibri))
            .register(ContextResolver<ObjectMapper> { ObjectMapper().registerModule(KotlinModule()) })
            .register(JacksonFeature::class.java)

        val servlet = ServletHolder(ServletContainer(jerseyConfig))

        val server = Server(3333)
        val context = ServletContextHandler(server, "/*")
        context.addServlet(servlet, "/*")

        try {
            server.start()
            server.join()
        } catch (e: Exception) {
            println("Error with server: $e")
        } finally {
            server.destroy()
        }
    }
    internalApiThread.start()
    // XmppApi
    val xmppApi = XmppApi(jibriManager = jibri, xmppConfigs = jibri.config.xmppEnvironments)

    // HttpApi
    Thread {
        val jerseyConfig = ResourceConfig()
        jerseyConfig.register(HttpApi(jibri))
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
            println("Error with server: $e")
        } finally {
            server.destroy()
        }
    }.start()

    // Wait on the internal API thread to prevent Main from exiting
    internalApiThread.join()
}
