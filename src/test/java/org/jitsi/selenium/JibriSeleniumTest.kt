package org.jitsi.selenium

import org.testng.Assert.*
import org.testng.annotations.Test

class JibriSeleniumTest
{

    @Test
    fun test()
    {
        val options = JibriSeleniumOptions(
                baseUrl = "https://meet.jit.si")
        val selenium = JibriSelenium(options)

        selenium.joinCall("test")
        Thread.sleep(10000);
        selenium.quitBrowser()
    }
}