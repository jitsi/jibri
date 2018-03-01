package org.jitsi.jibri.selenium.pageobjects

import org.openqa.selenium.remote.RemoteWebDriver

/**
 * This class represents a page object for the home page (i.e. on the domain
 * but not in a call) for a jitsi-meet server
 */
class HomePage(driver: RemoteWebDriver) : AbstractPageObject(driver)