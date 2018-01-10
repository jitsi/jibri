package org.jitsi.selenium.pageobjects

import org.openqa.selenium.WebDriver

class CallPage
{
    companion object {
        fun visit(driver: WebDriver, baseDomain: String, callName: String)
        {
            driver.get(baseDomain + "/" + callName)
        }
    }
}