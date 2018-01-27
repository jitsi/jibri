package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.WebDriver

/**
 * [AbstractPageObject] is a page object class containing logic common to
 * all page object instances
 */
open class AbstractPageObject(protected val driver: WebDriver) {

    fun visit(callUrlInfo: CallUrlInfo)
    {
        driver.get(callUrlInfo.callUrl)
    }
}