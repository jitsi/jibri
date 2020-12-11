/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */

package org.jitsi.jibri

import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jitsi.jibri.api.http.HttpApi
import org.jitsi.jibri.api.http.internal.InternalHttpApi
import org.jitsi.jibri.api.xmpp.XmppApi
import org.jitsi.jibri.api.xmpp.XmppEnvironment
import org.jitsi.jibri.api.xmpp.toXmppEnvironment
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.webhooks.v1.WebhookClient
import org.jitsi.metaconfig.MetaconfigLogger
import org.jitsi.metaconfig.MetaconfigSettings
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.LoggerImpl
import kotlin.system.exitProcess

val logger: Logger = LoggerImpl("org.jitsi.jibri.Main")

fun main() {
    setupMetaconfigLogger()
    val jibriManager = JibriManager()

    val webhookClient = WebhookClient(JibriConfig.jibriId)
    JibriConfig.webhookSubscribers.forEach(webhookClient::addSubscriber)

    val webhookStateUpdater = GlobalScope.launch(CoroutineName("webhook client updater")) {
        jibriManager.currentState.collect {
            webhookClient.updateState(it)
        }
    }

    val cleanupAndExit = { exitCode: Int ->
        // TODO: Needed?
        webhookStateUpdater.cancel("Shutting down")
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
        jibriManager.shutdown()
        logger.info("Service stopped")
        cleanupAndExit(255)
    }

    logger.info("Using port ${InternalHttpApi.port} for internal HTTP API")
    with(InternalHttpApi(configChangedHandler, gracefulShutdownHandler, shutdownHandler)) {
        embeddedServer(Jetty, port = InternalHttpApi.port) {
            internalApiModule()
        }.start()
    }

    @Suppress("UNUSED_VARIABLE") val xmppApi = XmppApi(
        jibriManager,
        JibriConfig.xmppEnvironments
    ).apply {
        joinMucs()
    }

    logger.info("Using port ${HttpApi.port} for HTTP API")
    with(HttpApi(jibriManager)) {
        embeddedServer(Jetty, port = HttpApi.port) {
            apiModule()
        }
    }.start()
}

// Not to be confused with the old Jibriconfig, this is 'top-level' config for Jibri.
// TODO: have the xmpp client read the environments as part of its own config?
private class JibriConfig {
    companion object {
        val jibriId: String by config("jibri.id".from(Config.configSource))
        val webhookSubscribers: List<String>by config("jibri.webhook.subscribers".from(Config.configSource))

        val xmppEnvironments: List<XmppEnvironment> by config {
            "jibri.api.xmpp.environments"
                .from(Config.configSource)
                .convertFrom<List<com.typesafe.config.Config>> { envConfigs ->
                    envConfigs.map { it.toXmppEnvironment() }
                }
        }
    }
}

/**
 * Wire the jitsi-metaconfig logger into ours
 */
private fun setupMetaconfigLogger() {
    val configLogger = LoggerImpl("org.jitsi.jibri.config")
    MetaconfigSettings.logger = object : MetaconfigLogger {
        override fun debug(block: () -> String) {
            configLogger.debug(block)
        }
        override fun error(block: () -> String) {
            configLogger.error(block())
        }
        override fun warn(block: () -> String) {
            configLogger.warn(block)
        }
    }
}
