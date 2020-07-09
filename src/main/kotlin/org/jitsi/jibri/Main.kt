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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import kotlinx.coroutines.CancellationException
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
import org.jitsi.jibri.config.JibriConfig
import org.jitsi.jibri.statsd.JibriStatsDClient
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
import org.jitsi.jibri.webhooks.v1.WebhookClient
import java.io.File
import java.util.concurrent.TimeUnit
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
    argParser.addArgument("--internal-http-port")
        .type(Int::class.java)
        .setDefault(3333)
        .help("Port to start the internal HTTP server on")
    argParser.addArgument("--http-api-port")
        .type(Int::class.java)
        .setDefault(2222)
        .help("Port to start the HTTP API server on")

    logger.info("Jibri run with args ${args.asList()}")
    val ns = argParser.parseArgs(args)
    val configFilePath = ns.getString("config")
    logger.info("Using config file $configFilePath")
    val internalHttpPort = ns.getInt("internal_http_port")
    logger.info("Using port $internalHttpPort for internal HTTP API")
    val httpApiPort = ns.getInt("http_api_port")
    logger.info("Using port $httpApiPort for the HTTP API")

    val jibriConfigFile = File(configFilePath)
    if (!jibriConfigFile.exists()) {
        logger.error("Error: Config file $configFilePath doesn't exist")
        exitProcess(1)
    }
    val jibriStatusManager = JibriStatusManager()
    val jibriConfig = loadConfig(jibriConfigFile) ?: exitProcess(1)
    val statsDClient: JibriStatsDClient? = if (jibriConfig.enabledStatsD) JibriStatsDClient() else null
    val jibriManager = JibriManager(jibriConfig, statsDClient = statsDClient)
    jibriManager.addStatusHandler { jibriStatus ->
        when (jibriStatus) {
            is ComponentBusyStatus -> {
                jibriStatusManager.busyStatus = jibriStatus
            }
            is ComponentHealthStatus -> {
                jibriStatusManager.updateHealth("JibriManager", jibriStatus)
            }
            else -> {
                logger.error("Unrecognized status from JibriManager: ${jibriStatus.javaClass} $jibriStatus")
            }
        }
    }

    val webhookClient = WebhookClient(jibriConfig.jibriId)

    jibriStatusManager.addStatusHandler {
        webhookClient.updateStatus(it)
    }
    jibriConfig.webhookSubscribers.forEach { webhookClient.addSubscriber(it) }
    val statusUpdaterTask = TaskPools.recurringTasksPool.scheduleAtFixedRate(
        1,
        TimeUnit.MINUTES
    ) {
        webhookClient.updateStatus(jibriStatusManager.overallStatus)
    }

    val cleanupAndExit = { exitCode: Int ->
        statusUpdaterTask.cancel(true)
        try {
            statusUpdaterTask.get(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            when (t) {
                is CancellationException -> {}
                else -> logger.error("Error cleaning up status updater task: $t")
            }
        }
        exitProcess(exitCode)
    }

    val configChangedHandler = {
        logger.info("The config file has changed, waiting for Jibri to be idle before exiting")
        jibriManager.executeWhenIdle {
            logger.info("Jibri is idle and there are config file changes, exiting")
            // Exit so we can be restarted and load the new config
            cleanupAndExit(0)
        }
    }
    val gracefulShutdownHandler = {
        logger.info("Jibri has been told to graceful shutdown, waiting to be idle before exiting")
        jibriManager.executeWhenIdle {
            logger.info("Jibri is idle and has been told to gracefully shutdown, exiting")
            // Exit with code 255 to indicate we do not want process restart
            cleanupAndExit(255)
        }
    }
    val shutdownHandler = {
        logger.info("Jibri has been told to shutdown, stopping any active service")
        jibriManager.stopService()
        logger.info("Service stopped")
        cleanupAndExit(255)
    }

    with (InternalHttpApi(configChangedHandler, gracefulShutdownHandler, shutdownHandler)) {
        embeddedServer(Jetty, port = internalHttpPort) {
            internalApiModule()
        }.start()
    }

    // XmppApi
    val xmppApi = XmppApi(
        jibriManager = jibriManager,
        xmppConfigs = jibriConfig.xmppEnvironments,
        jibriStatusManager = jibriStatusManager
    )
    xmppApi.start()

    // HttpApi
    with(HttpApi(jibriManager, jibriStatusManager)) {
        embeddedServer(Jetty, port = httpApiPort) {
            apiModule()
        }
    }.start()
}
