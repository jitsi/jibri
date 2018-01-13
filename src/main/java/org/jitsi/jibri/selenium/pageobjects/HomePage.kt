package org.jitsi.jibri.selenium.pageobjects

import org.openqa.selenium.WebDriver

/**
 * This class represents a page object for the home page (i.e. on the domain
 * but not in a call)
 */
class HomePage(private val driver: WebDriver)
{
    fun visit(baseDomain: String)
    {
        driver.get(baseDomain)
    }
}