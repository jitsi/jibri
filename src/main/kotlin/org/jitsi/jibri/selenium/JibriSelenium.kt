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

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.HomePage
import org.jitsi.jibri.selenium.status_checks.CallStatusCheck
import org.jitsi.jibri.selenium.status_checks.EmptyCallStatusCheck
import org.jitsi.jibri.selenium.status_checks.MediaReceivedStatusCheck
import org.jitsi.jibri.selenium.util.BrowserFileHandler
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
import org.jitsi.jibri.util.getLoggerWithHandler
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.CapabilityType
import org.openqa.selenium.remote.UnreachableBrowserException
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    val extraChromeCommandLineFlags: List<String> = listOf(),
    /**
     * How long we should stay in a call with no other participants before quitting
     */
    val emptyCallTimeout: Duration = EmptyCallStatusCheck.DEFAULT_CALL_EMPTY_TIMEOUT
)

val SIP_GW_URL_OPTIONS = listOf(
    "config.iAmRecorder=true",
    "config.iAmSipGateway=true",
    "config.ignoreStartMuted=true",
    "config.analytics.disabled=true",
    "config.p2p.enabled=false",
    "config.prejoinPageEnabled=false",
    "config.requireDisplayName=false"
)

val RECORDING_URL_OPTIONS = listOf(
    "config.iAmRecorder=true",
    "config.externalConnectUrl=null",
    "config.startWithAudioMuted=true",
    "config.startWithVideoMuted=true",
    "interfaceConfig.APP_NAME=\"Jibri\"",
    "config.analytics.disabled=true",
    "config.p2p.enabled=false",
    "config.prejoinPageEnabled=false",
    "config.requireDisplayName=false"
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
    private val jibriSeleniumOptions: JibriSeleniumOptions = JibriSeleniumOptions()
) : StatusPublisher<ComponentState>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private var chromeDriver: ChromeDriver
    private var currCallUrl: String? = null
    private val stateMachine = SeleniumStateMachine()
    private var shuttingDown = AtomicBoolean(false)
    private val chromeOpts: List<String> by config("jibri.chrome.flags".from(Config.configSource))

    /**
     * A task which executes at an interval and checks various aspects of the call to make sure things are
     * working correctly
     */
    private var recurringCallStatusCheckTask: ScheduledFuture<*>? = null
    private val callStatusChecks: List<CallStatusCheck>

    /**
     * Set up default chrome driver options (using fake device, etc.)
      */
    init {
        System.setProperty("webdriver.chrome.logfile", "/tmp/chromedriver.log")
        val chromeOptions = ChromeOptions()
        chromeOptions.addArguments(chromeOpts)
        chromeOptions.setExperimentalOption("w3c", false)
        chromeOptions.addArguments(jibriSeleniumOptions.extraChromeCommandLineFlags)
        val chromeDriverService = ChromeDriverService.Builder().withEnvironment(
            mapOf("DISPLAY" to jibriSeleniumOptions.display)
        ).build()
        val logPrefs = LoggingPreferences()
        logPrefs.enable(LogType.DRIVER, Level.ALL)
        chromeOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs)
        chromeDriver = ChromeDriver(chromeDriverService, chromeOptions)
        chromeDriver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS)

        // Note that the order here is important: we always want to check for no participants before we check
        // for media being received, since the call being empty will also mean Jibri is not receiving media but should
        // not cause Jibri to go unhealthy (like not receiving media when there are others in the call will).
        callStatusChecks = listOf(
            EmptyCallStatusCheck(logger, callEmptyTimeout = jibriSeleniumOptions.emptyCallTimeout),
            MediaReceivedStatusCheck(logger)
        )
        stateMachine.onStateTransition(this::onSeleniumStateChange)
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

    private fun onSeleniumStateChange(oldState: ComponentState, newState: ComponentState) {
        logger.info("Transitioning from state $oldState to $newState")
        publishStatus(newState)
    }

    private fun startRecurringCallStatusChecks() {
        // We fire all state transitions in the ioPool, otherwise we may try and cancel the
        // recurringCallStatusCheckTask from within the thread it was executing in.  Another solution would've been
        // to pass 'false' to recurringCallStatusCheckTask.cancel, but it felt cleaner to separate the threads
        val transitionState = { event: SeleniumEvent ->
            TaskPools.ioPool.submit {
                stateMachine.transition(event)
            }
        }
        recurringCallStatusCheckTask = TaskPools.recurringTasksPool.scheduleAtFixedRate(15, TimeUnit.SECONDS, 15) {
            val callPage = CallPage(chromeDriver)
            try {
                // Run through each of the checks.  If we hit one that returns an event, then we stop and process the
                // state transition from that event. Note: it's intentional that we stop at the first check that fails
                // and 'asSequence' is necessary to do that.
                val event = callStatusChecks
                        .asSequence()
                        .map { check -> check.run(callPage) }
                        .firstOrNull { result -> result != null }
                if (event != null) {
                    logger.info("Recurring call status checks generated event $event")
                    transitionState(event)
                }
            } catch (t: TimeoutException) {
                // We've found that a timeout here often implies chrome has hung so consider this as an error
                // which will result in this Jibri being marked as unhealthy
                logger.error("Javascript timed out, assuming chrome has hung", t)
                transitionState(SeleniumEvent.ChromeHung)
            } catch (t: UnreachableBrowserException) {
                if (!shuttingDown.get()) {
                    logger.error("Can't reach browser", t)
                    transitionState(SeleniumEvent.ChromeHung)
                }
            } catch (t: Throwable) {
                logger.error("Error while running call status checks", t)
                // We'll just assume anything here is an issue
                transitionState(SeleniumEvent.ChromeHung)
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

    fun addToPresence(key: String, value: String): Boolean = CallPage(chromeDriver).addToPresence(key, value)

    fun sendPresence(): Boolean = CallPage(chromeDriver).sendPresence()

    /**
     * Join a a web call with Selenium
     */
    fun joinCall(callUrlInfo: CallUrlInfo, xmppCredentials: XmppCredentials? = null) {
        // These are all blocking calls, so offload the work to another thread
        TaskPools.ioPool.submit {
            try {
                HomePage(chromeDriver).visit(callUrlInfo.baseUrl)

                val localStorageValues = mutableMapOf(
                        "displayname" to jibriSeleniumOptions.displayName,
                        "email" to jibriSeleniumOptions.email,
                        "callStatsUserName" to "jibri"
                )
                xmppCredentials?.let {
                    localStorageValues["xmpp_username_override"] =
                        "${xmppCredentials.username}@${xmppCredentials.domain}"
                    localStorageValues["xmpp_password_override"] = xmppCredentials.password
                }
                setLocalStorageValues(localStorageValues)
                if (!CallPage(chromeDriver).visit(callUrlInfo.callUrl)) {
                    stateMachine.transition(SeleniumEvent.FailedToJoinCall)
                } else {
                    startRecurringCallStatusChecks()
                    addParticipantTracker()
                    currCallUrl = callUrlInfo.callUrl
                    stateMachine.transition(SeleniumEvent.CallJoined)
                }
            } catch (t: Throwable) {
                logger.error("An error occurred while joining the call", t)
                stateMachine.transition(SeleniumEvent.FailedToJoinCall)
            }
        }
    }

    fun getParticipants(): List<Map<String, Any>> {
        return CallPage(chromeDriver).getParticipants()
    }

    fun leaveCallAndQuitBrowser() {
        logger.info("Leaving call and quitting browser")
        shuttingDown.set(true)
        recurringCallStatusCheckTask?.cancel(true)
        logger.info("Recurring call status checks cancelled")

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

    companion object {
        private val browserOutputLogger = getLoggerWithHandler("browser", BrowserFileHandler())
        const val COMPONENT_ID = "Selenium"
    }
}
