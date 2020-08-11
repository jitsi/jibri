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

import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import kotlinx.coroutines.CancellationException
import net.sourceforge.argparse4j.ArgumentParsers
import org.jitsi.jibri.api.http.HttpApi
import org.jitsi.jibri.api.http.internal.InternalHttpApi
import org.jitsi.jibri.api.xmpp.XmppApi
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.config.loadConfigFromFile
import org.jitsi.jibri.config.toXmppEnvironment
import org.jitsi.jibri.status.ComponentBusyStatus
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
import org.jitsi.jibri.webhooks.v1.WebhookClient
import org.jitsi.metaconfig.ConfigException
import org.jitsi.metaconfig.MapConfigSource
import org.jitsi.metaconfig.MetaconfigLogger
import org.jitsi.metaconfig.MetaconfigSettings
import org.jitsi.metaconfig.configSupplier
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.system.exitProcess

val logger: Logger = Logger.getLogger("org.jitsi.jibri.Main")

fun main(args: Array<String>) {
    setupMetaconfigLogger()
    handleCommandLineArgs(args)

    val jibriStatusManager = JibriStatusManager()
    val jibriManager = JibriManager()
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
    val jibriId = configSupplier<String> {
        "JibriConfig::jibriId" { Config.legacyConfigSource.jibriId!! }
        "jibri.id".from(Config.configSource)
    }.get()
    val webhookSubscribers = configSupplier<List<String>> {
        "jibri.webhook.subscribers".from(Config.configSource)
    }.get()

    val webhookClient = WebhookClient(jibriId)

    jibriStatusManager.addStatusHandler {
        webhookClient.updateStatus(it)
    }
    webhookSubscribers.forEach(webhookClient::addSubscriber)
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

    logger.info("Using port ${InternalHttpApi.port} for internal HTTP API")

    with(InternalHttpApi(configChangedHandler, gracefulShutdownHandler, shutdownHandler)) {
        embeddedServer(Jetty, port = InternalHttpApi.port) {
            internalApiModule()
        }.start()
    }

    val xmppEnvironments = configSupplier<List<XmppEnvironmentConfig>> {
        "JibriConfig::xmppEnvironments" {
            Config.legacyConfigSource.xmppEnvironments.takeIf { it?.isNotEmpty() == true }
                ?: throw ConfigException.UnableToRetrieve.NotFound("Considering empty XMPP envs list as not found")
        }
        "jibri.api.xmpp.environments"
            .from(Config.configSource)
            .convertFrom<List<com.typesafe.config.Config>> { envConfigs -> envConfigs.map { it.toXmppEnvironment() } }
    }.get()

    // XmppApi
    val xmppApi = XmppApi(
        jibriManager = jibriManager,
        xmppConfigs = xmppEnvironments,
        jibriStatusManager = jibriStatusManager
    )
    xmppApi.start()

    logger.info("Using port ${HttpApi.port} for HTTP API")

    // HttpApi
    with(HttpApi(jibriManager, jibriStatusManager)) {
        embeddedServer(Jetty, port = HttpApi.port) {
            apiModule()
        }
    }.start()
}

private fun handleCommandLineArgs(args: Array<String>) {
    val argParser = ArgumentParsers.newFor("Jibri").build()
        .defaultHelp(true)
        .description("Start Jibri")
    argParser.addArgument("-c", "--config")
        .required(true)
        .type(String::class.java)
        .help("Path to the jibri config file")
    argParser.addArgument("--internal-http-port")
        .type(Int::class.java)
        .help("Port to start the internal HTTP server on")
    argParser.addArgument("--http-api-port")
        .type(Int::class.java)
        .help("Port to start the HTTP API server on")

    logger.info("Jibri run with args ${args.asList()}")
    val ns = argParser.parseArgs(args)
    val configFilePath = ns.getString("config")
    val internalHttpPort: Int? = ns.getInt("internal_http_port")
    val httpApiPort: Int? = ns.getInt("http_api_port")

    // Map the command line arguments into a ConfigSource
    Config.commandLineArgs = MapConfigSource("command line args") {
        internalHttpPort?.let {
            put("internal_http_port", it)
        }
        httpApiPort?.let {
            put("http_api_port", it)
        }
    }

    setupLegacyConfig(configFilePath)
}

/**
 * Parse the legacy config file and set it in [Config] as the legacy config source
 */
private fun setupLegacyConfig(configFilePath: String) {
    logger.info("Checking legacy config file $configFilePath")
    val jibriConfigFile = File(configFilePath)
    if (!jibriConfigFile.exists()) {
        logger.info("Legacy config file $configFilePath doesn't exist")
        return
    }

    val jibriConfig = loadConfigFromFile(jibriConfigFile) ?: run {
        logger.info("Parsing legacy config file failed")
        return
    }
    Config.legacyConfigSource = jibriConfig
}

/**
 * Wire the jitsi-metaconfig logger into ours
 */
private fun setupMetaconfigLogger() {
    val configLogger = Logger.getLogger("org.jitsi.jibri.config")
    MetaconfigSettings.logger = object : MetaconfigLogger {
        override fun debug(block: () -> String) {
            configLogger.fine(block)
        }
        override fun error(block: () -> String) {
            configLogger.error(block())
        }
        override fun warn(block: () -> String) {
            configLogger.warning(block)
        }
    }
}
