package org.jitsi.jibri.selenium

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.HomePage
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.util.Duration
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.scheduleAtFixedRate
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * The [JibriSelenium] class is responsible for all of the interactions with
 * Selenium.  It:
 * 1) Owns the webdriver
 * 2) Handles passing the proper options to Chrome
 * 3) Sets the necessary localstorage variables before joining a call
 */
class JibriSelenium(
        private val jibriSeleniumOptions: JibriSeleniumOptions,
        private val executor: ScheduledExecutorService
) : StatusPublisher<JibriServiceStatus>() {
    private val logger = Logger.getLogger(this::class.simpleName)
    var chromeDriver: ChromeDriver
    var baseUrl: String
    val URL_OPTIONS = listOf(
        "config.iAmRecorder=true",
        "config.externalConnectUrl=null",
        "interfaceConfig.APP_NAME=\"Jibri\""
        )
    private var emptyCallTask: ScheduledFuture<*>? = null

    /**
     * Set up default chrome driver options (using fake device, etc.)
      */
    init {
        baseUrl = jibriSeleniumOptions.callParams.callUrlInfo.baseUrl
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
        val chromeDriverService = ChromeDriverService.Builder().withEnvironment(
            mapOf("DISPLAY" to ":0")
        ).build()
        chromeDriver = ChromeDriver(chromeDriverService, chromeOptions)
    }

    /**
     * Set various values to be put in local storage.  NOTE: the driver
     * should have already navigated to the desired page
     */
    private fun setJibriIdentifiers(vararg keyValues: Pair<String, String>) {
        for ((key, value) in keyValues) {
            chromeDriver.executeScript("window.localStorage.setItem('$key', '$value')")
        }
    }

    private fun addEmptyCallDetector() {
        var numTimesEmpty = 0
        emptyCallTask = executor.scheduleAtFixedRate(
            period = Duration( 15, TimeUnit.SECONDS),
            action = object : Runnable {
                override fun run() {
                    // >1 since the count will include jibri itself
                    if (CallPage(chromeDriver).getNumParticipants(chromeDriver) > 1) {
                        numTimesEmpty = 0
                    } else {
                        numTimesEmpty++
                    }
                    if (numTimesEmpty >= 2) {
                        logger.info("Jibri has been in a lonely call for 30 seconds, marking as finished")
                        emptyCallTask?.cancel(false)
                        publishStatus(JibriServiceStatus.FINISHED)
                    }
                }
            }
        )
    }

    /**
     * Join a a web call with Selenium
     */
    fun joinCall(callName: String) {
        HomePage(chromeDriver).visit(CallUrlInfo(baseUrl, ""))
        setJibriIdentifiers(
                Pair("displayname", "TODO"),
                Pair("email", "TODO"),
                Pair("xmpp_username_override", "${jibriSeleniumOptions.callParams.callLoginParams.username}@${jibriSeleniumOptions.callParams.callLoginParams.domain}"),
                Pair("xmpp_password_override", jibriSeleniumOptions.callParams.callLoginParams.password),
                Pair("callStatsUserName", "jibri")
        )
        CallPage(chromeDriver).visit(CallUrlInfo(baseUrl, "$callName#${URL_OPTIONS.joinToString("&")}"))
        addEmptyCallDetector()
    }

    fun leaveCallAndQuitBrowser() {
        //TODO: we can't leave the call cleanly via clicking the 'leave call' button
        // because they are hidden from the UI when jibri joins.  Should we issue a js
        // command to leave cleanly rather than just qutting the driver?
        //CallPage(chromeDriver).leave()
        chromeDriver.quit()
    }

    //TODO: helper func to verify connectivity
}