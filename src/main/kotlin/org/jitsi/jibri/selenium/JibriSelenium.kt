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

package org.jitsi.jibri.selenium

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.HomePage
import org.jitsi.jibri.selenium.util.BrowserFileHandler
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.CapabilityType
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Parameters needed for joining the call in Selenium
 */
data class CallParams(
    val callUrlInfo: CallUrlInfo
)

/**
 * Options that can be passed to [JibriSelenium]
 */
data class JibriSeleniumOptions(
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
    val extraChromeCommandLineFlags: List<String> = listOf()
)

val SIP_GW_URL_OPTIONS = listOf(
    "config.iAmRecorder=true",
    "config.iAmSipGateway=true",
    "config.ignoreStartMuted=true"
)

val RECORDING_URL_OPTIONS = listOf(
    "config.iAmRecorder=true",
    "config.externalConnectUrl=null",
    "config.startWithAudioMuted=true",
    "config.startWithVideoMuted=true",
    "interfaceConfig.APP_NAME=\"Jibri\""
)

/**
 * The [JibriSelenium] class is responsible for all of the interactions with
 * Selenium.  It:
 * 1) Owns the webdriver
 * 2) Handles passing the proper options to Chrome
 * 3) Sets the necessary localstorage variables before joining a call
 * It implements [StatusPublisher] to publish its status
 */
class JibriSelenium(
    private val jibriSeleniumOptions: JibriSeleniumOptions = JibriSeleniumOptions(),
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : StatusPublisher<JibriServiceStatus>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val browserOutputLogger = Logger.getLogger("browser")
    private var chromeDriver: ChromeDriver
    private var currCallUrl: String? = null

    /**
     * A task which checks if Jibri is alone in the call
     */
    private var emptyCallTask: ScheduledFuture<*>? = null

    /**
     * Set up default chrome driver options (using fake device, etc.)
      */
    init {
        browserOutputLogger.useParentHandlers = false
        browserOutputLogger.addHandler(BrowserFileHandler())
        System.setProperty("webdriver.chrome.logfile", "/tmp/chromedriver.log")
        val chromeOptions = ChromeOptions()
        chromeOptions.addArguments(
                "--use-fake-ui-for-media-stream",
                "--start-maximized",
                "--kiosk",
                "--enabled",
                "--enable-logging",
                "--vmodule=*=3",
                "--disable-infobars",
                "--alsa-output-device=plug:amix"
        )
        chromeOptions.addArguments(jibriSeleniumOptions.extraChromeCommandLineFlags)
        val chromeDriverService = ChromeDriverService.Builder().withEnvironment(
            mapOf("DISPLAY" to jibriSeleniumOptions.display)
        ).build()
        val logPrefs = LoggingPreferences()
        logPrefs.enable(LogType.DRIVER, Level.ALL)
        chromeOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs)
        chromeDriver = ChromeDriver(chromeDriverService, chromeOptions)
    }

    /**
     * Set various values to be put in local storage.  NOTE: the driver
     * should have already navigated to the desired page
     */
    private fun setLocalStorageValues(keyValues: Map<String, String>) {
        for ((key, value) in keyValues) {
            chromeDriver.executeScript("window.localStorage.setItem('$key', '$value')")
        }
    }

    /**
     * Check if Jibri is the only participant in the call.  If it is the only
     * participant for 30 seconds, it will leave the call.
     */
    private fun addEmptyCallDetector() {
        var numTimesEmpty = 0
        emptyCallTask = executor.scheduleAtFixedRate(15, TimeUnit.SECONDS, 15) {
            try {
                // >1 since the count will include jibri itself
                if (CallPage(chromeDriver).getNumParticipants() > 1) {
                    numTimesEmpty = 0
                } else {
                    numTimesEmpty++
                }
                if (numTimesEmpty >= 2) {
                    logger.info("Jibri has been in a lonely call for 30 seconds, marking as finished")
                    emptyCallTask?.cancel(false)
                    publishStatus(JibriServiceStatus.FINISHED)
                }
            } catch (t: Throwable) {
                logger.error("Error while checking for empty call state: $t")
            }
        }
    }

    /**
     * Keep track of all the participants who take part in the call while
     * Jibri is active
     */
    private fun addParticipantTracker() {
        CallPage(chromeDriver).injectParticipantTrackerScript()
    }

    fun addToPresence(key: String, value: String): Boolean =
        CallPage(chromeDriver).addToPresence(key, value)

    fun sendPresence(): Boolean = CallPage(chromeDriver).sendPresence()

    /**
     * Join a a web call with Selenium
     */
    fun joinCall(callUrlInfo: CallUrlInfo, xmppCredentials: XmppCredentials? = null): Boolean {
        HomePage(chromeDriver).visit(callUrlInfo.baseUrl)

        val localStorageValues = mutableMapOf(
            "displayname" to jibriSeleniumOptions.displayName,
            "email" to jibriSeleniumOptions.email,
            "callStatsUserName" to "jibri"
        )
        xmppCredentials?.let {
            localStorageValues["xmpp_username_override"] = "${xmppCredentials.username}@${xmppCredentials.domain}"
            localStorageValues["xmpp_password_override"] = xmppCredentials.password
        }
        setLocalStorageValues(localStorageValues)
        if (!CallPage(chromeDriver).visit(callUrlInfo.callUrl)) {
            return false
        }
        addEmptyCallDetector()
        addParticipantTracker()
        currCallUrl = callUrlInfo.callUrl
        return true
    }

    fun getParticipants(): List<Map<String, Any>> {
        return CallPage(chromeDriver).getParticipants()
    }

    fun leaveCallAndQuitBrowser() {
        emptyCallTask?.cancel(true)

        browserOutputLogger.info("Logs for call $currCallUrl")
        chromeDriver.manage().logs().availableLogTypes.forEach { logType ->
            val logEntries = chromeDriver.manage().logs().get(logType)
            logger.info("Got ${logEntries.all.size} log entries for type $logType")
            browserOutputLogger.info("========= TYPE=$logType ===========")
            logEntries.all.forEach {
                browserOutputLogger.info(it.toString())
            }
        }
        logger.info("Leaving web call")
        CallPage(chromeDriver).leave()
        currCallUrl = null
        logger.info("Quitting chrome driver")
        chromeDriver.quit()
        logger.info("Chrome driver quit")
    }

    // TODO: helper func to verify connectivity
}
