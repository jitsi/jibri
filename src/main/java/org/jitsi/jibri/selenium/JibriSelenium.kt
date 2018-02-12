package org.jitsi.jibri.selenium

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.HomePage
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions

/**
 * The [JibriSelenium] class is responsible for all of the interactions with
 * Selenium.  It:
 * 1) Owns the webdriver
 * 2) Handles passing the proper options to Chrome
 * 3) Sets the necessary localstorage variables before joining a call
 */
class JibriSelenium(val jibriSeleniumOptions: JibriSeleniumOptions)
{
    var chromeDriver: ChromeDriver
    var baseUrl: String
    val URL_OPTIONS = listOf(
        "config.iAmRecorder=true",
        "config.externalConnectUrl=null",
        "interfaceConfig.APP_NAME=\"Jibri\""
        )

    /**
     * Set up default chrome driver options (using fake device, etc.)
      */
    init {
        baseUrl = jibriSeleniumOptions.callParams.callUrlInfo.baseUrl
        System.setProperty("webdriver.chrome.logfile", "/tmp/chromedriver.log");
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
    private fun setJibriIdentifiers(vararg keyValues: Pair<String, String>)
    {
        for ((key, value) in keyValues)
        {
            chromeDriver.executeScript("window.localStorage.setItem('$key', '$value')");
        }
    }

    /**
     * Join a a web call with Selenium
     */
    fun joinCall(callName: String)
    {
        HomePage(chromeDriver).visit(CallUrlInfo(baseUrl, ""))
        setJibriIdentifiers(
                Pair("displayname", "TODO"),
                Pair("email", "TODO"),
                Pair("xmpp_username_override", "${jibriSeleniumOptions.callParams.callLoginParams.username}@${jibriSeleniumOptions.callParams.callLoginParams.domain}"),
                Pair("xmpp_password_override", jibriSeleniumOptions.callParams.callLoginParams.password),
                Pair("callStatsUserName", "jibri")
        )
        CallPage(chromeDriver).visit(CallUrlInfo(baseUrl, "$callName#${URL_OPTIONS.joinToString("&")}"))
    }

    fun leaveCallAndQuitBrowser()
    {
        //TODO: we can't leave the call cleanly via clicking the 'leave call' button
        // because they are hidden from the UI when jibri joins.  Should we issue a js
        // command to leave cleanly rather than just qutting the driver?
        //CallPage(chromeDriver).leave()
        chromeDriver.quit()
    }

    //TODO: helper func to verify connectivity
}