package org.jitsi.selenium.pageobjects

import org.openqa.selenium.WebDriver

/**
 * This class represents a page object for the home page (i.e. on the domain
 * but not in a call)
 */
class HomePage
{
    companion object {
        fun visit(driver: WebDriver, baseDomain: String)
        {
            driver.get(baseDomain)
        }
    }
}