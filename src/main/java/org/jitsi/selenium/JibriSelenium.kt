package org.jitsi.selenium

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions

class JibriSelenium(jibriSeleniumOptions: JibriSeleniumOptions)
{
    // Set up default chrome driver options (using fake device, etc.)
    init {
        val chromeOptions: ChromeOptions = ChromeOptions()
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
    }
}