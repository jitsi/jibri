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

package org.jitsi.jibri

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.sourceforge.argparse4j.ArgumentParsers
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.jitsi.jibri.api.http.HttpApi
import org.jitsi.jibri.api.http.internal.InternalHttpApi
import org.jitsi.jibri.api.xmpp.XmppApi
import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.util.extensions.error
import java.io.File
import java.util.logging.Logger
import javax.ws.rs.ext.ContextResolver
import kotlin.system.exitProcess

val logger: Logger = Logger.getLogger("org.jitsi.jibri.Main")

fun loadConfig(configFile: File): JibriConfig? {
    return try {
        val config: JibriConfig = jacksonObjectMapper()
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .readValue(configFile)
        logger.info("Parsed config:\n$config")
        config
    } catch (e: MissingKotlinParameterException) {
        logger.error("A required config parameter was missing: ${e.originalMessage}")
        null
    } catch (e: UnrecognizedPropertyException) {
        logger.error("An unrecognized config parameter was found: ${e.originalMessage}")
        null
    } catch (e: InvalidFormatException) {
        logger.error("A config parameter was incorrectly formatted: ${e.localizedMessage}")
        null
    }
}

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
    logger.info("Using config file $configFilePath")

    val jibriConfigFile = File(configFilePath)
    if (!jibriConfigFile.exists()) {
        logger.error("Error: Config file $configFilePath doesn't exist")
        exitProcess(1)
    }
    val jibriConfig = loadConfig(jibriConfigFile) ?: exitProcess(1)
    val jibri = JibriManager(jibriConfig)

    // InternalHttpApi
    val configChangedHandler = {
        logger.info("The config file has changed, waiting for Jibri to be idle before exiting")
        jibri.executeWhenIdle {
            logger.info("Jibri is idle and there are config file changes, exiting")
            // Exit so we can be restarted and load the new config
            exitProcess(0)
        }
    }
    val shutdownHandler = {
        logger.info("Jibri has been told to shutdown, stopping any active service")
        jibri.stopService()
        logger.info("Service stopped")
        exitProcess(0)
    }
    val internalHttpApi = InternalHttpApi(
        gracefulShutdownHandler = configChangedHandler,
        shutdownHandler = shutdownHandler
    )
    launchHttpServer(3333, internalHttpApi)

    // XmppApi
    val xmppApi = XmppApi(jibriManager = jibri, xmppConfigs = jibriConfig.xmppEnvironments)
    xmppApi.start()

    // HttpApi
    launchHttpServer(2222, HttpApi(jibri))
}

fun launchHttpServer(port: Int, component: Any) {
    val jerseyConfig = ResourceConfig(object : ResourceConfig() {
        init {
            register(ContextResolver<ObjectMapper> { ObjectMapper().registerKotlinModule() })
            registerInstances(component)
        }
    })
    val servlet = ServletHolder(ServletContainer(jerseyConfig))
    val server = Server(port)
    val context = ServletContextHandler(server, "/*")
    context.addServlet(servlet, "/*")
    server.start()
}
