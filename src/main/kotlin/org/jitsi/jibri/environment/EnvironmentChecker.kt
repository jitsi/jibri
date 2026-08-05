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

package org.jitsi.jibri.environment

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions

/**
 * Validates that the environment is ready to handle recording/streaming requests
 * by probing ChromeDriver at startup. Registers as UNHEALTHY until validation
 * succeeds, preventing Jicofo from dispatching requests to a Jibri that isn't
 * actually ready.
 */
class EnvironmentChecker(
    private val jibriStatusManager: JibriStatusManager,
    private val probeChrome: () -> Unit = ::defaultProbeChrome
) {
    private val logger = createLogger()

    init {
        jibriStatusManager.updateHealth(
            COMPONENT_ID,
            ComponentHealthStatus.UNHEALTHY,
            "Startup validation pending"
        )
    }

    /**
     * Attempt to start and immediately quit ChromeDriver to validate the environment.
     * Retries up to [maxAttempts] times with [retryDelayMs] between attempts.
     * Default values are read from config to allow operators to tune for their
     * environment (e.g., slow Xvfb startup after reboot).
     */
    fun validate(maxAttempts: Int = configMaxAttempts, retryDelayMs: Long = configRetryDelayMs) {
        for (attempt in 1..maxAttempts) {
            logger.info("ChromeDriver startup check attempt $attempt/$maxAttempts")
            try {
                probeChrome()
                logger.info("ChromeDriver startup check passed")
                jibriStatusManager.updateHealth(COMPONENT_ID, ComponentHealthStatus.HEALTHY)
                return
            } catch (t: Throwable) {
                logger.error("ChromeDriver startup check failed (attempt $attempt/$maxAttempts)", t)
                if (attempt < maxAttempts) {
                    Thread.sleep(retryDelayMs)
                }
            }
        }
        logger.error("ChromeDriver startup check failed after $maxAttempts attempts, Jibri will remain UNHEALTHY")
        jibriStatusManager.updateHealth(
            COMPONENT_ID,
            ComponentHealthStatus.UNHEALTHY,
            "ChromeDriver failed to start after $maxAttempts attempts"
        )
    }

    companion object {
        const val COMPONENT_ID = "EnvironmentCheck"
        private const val DISPLAY = ":0"
        private val chromeFlags: List<String> by config("jibri.chrome.flags".from(Config.configSource))
        private val configMaxAttempts: Int by config(
            "jibri.chrome.startup-check.max-attempts".from(Config.configSource)
        )
        private val configRetryDelayMs: Long by config(
            "jibri.chrome.startup-check.retry-delay".from(Config.configSource)
        )

        /**
         * Default probe: mirrors the ChromeDriver setup in JibriSelenium to validate
         * that Chrome and ChromeDriver are available and functional.
         */
        private fun defaultProbeChrome() {
            val chromeOptions = ChromeOptions()
            chromeOptions.addArguments(chromeFlags)
            val chromeDriverService = ChromeDriverService.Builder()
                .withEnvironment(mapOf("DISPLAY" to DISPLAY))
                .build()
            var driver: ChromeDriver? = null
            try {
                driver = ChromeDriver(chromeDriverService, chromeOptions)
            } finally {
                try {
                    driver?.quit()
                } catch (_: Throwable) {
                }
            }
        }
    }
}
