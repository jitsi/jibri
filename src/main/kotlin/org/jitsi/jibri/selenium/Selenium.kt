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

package org.jitsi.jibri.selenium

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.ChromeHung
import org.jitsi.jibri.FailedToJoinCall
import org.jitsi.jibri.JibriException
import org.jitsi.jibri.api.xmpp.XmppCredentials
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.HomePage
import org.jitsi.jibri.selenium.statuschecks.EmptyCallCheck
import org.jitsi.jibri.selenium.statuschecks.MediaReceivedCheck
import org.jitsi.jibri.util.getLoggerWithHandler
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.CapabilityType
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.logging.FileHandler
import java.util.logging.Level

/**
 * Parameters needed for joining the call in Selenium
 */
class CallParams(val callUrlInfo: CallUrlInfo, val callLogin: XmppCredentials)

/**
 * Options that can be passed to [Selenium]
 */
data class SeleniumOptions(
    /**
     * Which display selenium should be started on
     */
    val display: String = ":0",
    /**
     * The display name that should be used for jibri.  Note that this
     * is currently only used in the sipgateway gateway scenario; when doing
     * recording the jibri is 'invisible' in the call
     */
    val displayName: String = "",
    /**
     * The email that should be used for jibri.  Note that this
     * is currently only used in the sipgateway gateway scenario; when doing
     * recording the jibri is 'invisible' in the call
     */
    val email: String = "",
    /**
     * Chrome command line flags to add (in addition to the common
     * ones)
     */
    val extraChromeCommandLineFlags: List<String> = listOf(),
    /**
     * How long we should stay in a call with no other participants before quitting
     */
    val emptyCallTimeout: Duration = EmptyCallCheck.defaultCallEmptyTimeout
)

class Selenium(
    parentLogger: Logger,
    private val options: SeleniumOptions = SeleniumOptions()
) {
    private val logger = createChildLogger(parentLogger)
    private val callChecks = listOf(EmptyCallCheck(logger), MediaReceivedCheck(logger))
    private var currCallUrl: String? = null
    private val chromeDriver: ChromeDriver
    private val chromeOpts: List<String> by config("jibri.chrome.flags".from(Config.configSource))

    init {
        System.setProperty("webdriver.chrome.logfile", "/tmp/chromedriver.log")
        val chromeOptions = ChromeOptions().apply {
            setExperimentalOption("w3c", false)
            // Add args from config
            addArguments(chromeOpts)
            // Add args from passed-in options
            addArguments(options.extraChromeCommandLineFlags)
            setCapability(
                CapabilityType.LOGGING_PREFS,
                LoggingPreferences().apply {
                    enable(LogType.DRIVER, Level.ALL)
                }
            )
        }
        val chromeDriverService = ChromeDriverService.Builder().withEnvironment(
            mapOf("DISPLAY" to ":0")
        ).build()

        chromeDriver = ChromeDriver(chromeDriverService, chromeOptions)
        chromeDriver.manage().timeouts().pageLoadTimeout(1, TimeUnit.MINUTES)
    }

    fun joinCall(
        callUrlInfo: CallUrlInfo,
        xmppCredentials: XmppCredentials? = null
    ) {
        try {
            HomePage(chromeDriver).visit(callUrlInfo.baseUrl)
            val localStorageValues = buildMap<String, String> {
                put("displayname", options.displayName)
                put("email", options.email)
                put("callStatsUserName", "jibri")
                xmppCredentials?.let {
                    put("xmpp_username_override", "${it.username}@${it.domain}")
                    put("xmpp_password_override", it.password)
                }
            }
            setLocalStorageValues(localStorageValues)
            if (!CallPage(chromeDriver).visit(callUrlInfo.callUrl)) {
                throw FailedToJoinCall
            } else {
                addParticipantTracker()
                currCallUrl = callUrlInfo.callUrl
            }
        } catch (c: CancellationException) {
            logger.info("Cancelled, stopping call join")
            throw c
        } catch (t: Throwable) {
            logger.error("An error occurred while joining the call", t)
            // TODO: wrap in some JibriException?
            throw t
        }
    }

    suspend fun monitorCall() {
        val callPage = CallPage(chromeDriver)
        while (true) {
            logger.debug { "Running call status checks" }
            callChecks.forEach {
                try {
                    it.runCheck(callPage)
                } catch (t: Throwable) {
                    when (t) {
                        is JibriException -> throw t
                        else -> {
                            logger.error("Error running call checks, assuming chrome hung", t)
                            throw ChromeHung
                        }
                    }
                }
            }
            // Just use a constant interval for now
            delay(15000)
        }
    }

    fun getParticipants(): List<Map<String, Any>> {
        return CallPage(chromeDriver).getParticipants()
    }

    fun leaveCallAndQuitBrowser() {
        logger.info("Leaving call and quitting browser")
        val browserOutputLogger = getLoggerWithHandler("browser", BrowserFileHandler)
        browserOutputLogger.info("Logs for call $currCallUrl")
        try {
            chromeDriver.manage().logs().availableLogTypes.forEach { logType ->
                val logEntries = chromeDriver.manage().logs().get(logType)
                logger.info("Got ${logEntries.all.size} log entries for type $logType")
                browserOutputLogger.info("========= TYPE=$logType ===========")
                logEntries.all.forEach {
                    browserOutputLogger.info(it.toString())
                }
            }
        } catch (t: Throwable) {
            logger.error("Error trying to get chromedriver logs", t)
        }
        logger.info("Leaving web call")
        try {
            CallPage(chromeDriver).leave()
        } catch (t: Throwable) {
            logger.error("Error trying to leave the call", t)
        }
        currCallUrl = null
        logger.info("Quitting chrome driver")
        chromeDriver.quit()
        logger.info("Chrome driver quit")
    }

    /**
     * Keep track of all the participants who take part in the call while
     * Jibri is active
     */
    private fun addParticipantTracker() = CallPage(chromeDriver).injectParticipantTrackerScript()

    fun addToPresence(key: String, value: String): Boolean = CallPage(chromeDriver).addToPresence(key, value)

    fun sendPresence(): Boolean = CallPage(chromeDriver).sendPresence()

    /**
     * Set various values to be put in local storage.  NOTE: the driver
     * should have already navigated to the desired page
     */
    private fun setLocalStorageValues(keyValues: Map<String, String>) {
        for ((key, value) in keyValues) {
            chromeDriver.executeScript("window.localStorage.setItem('$key', '$value')")
        }
    }
}

private object BrowserFileHandler : FileHandler()

/**
 * An interface that can be taken by classes creating a selenium instance so it can be overridden by tests.
 * [SeleniumFactory#create] needs to be kept in sync with the [Selenium] constructor.
 */
interface SeleniumFactory {
    fun create(parentLogger: Logger, seleniumOptions: SeleniumOptions = SeleniumOptions()): Selenium
}

class SeleniumFactoryImpl : SeleniumFactory {
    override fun create(parentLogger: Logger, seleniumOptions: SeleniumOptions): Selenium {
        return Selenium(parentLogger, seleniumOptions)
    }
}
