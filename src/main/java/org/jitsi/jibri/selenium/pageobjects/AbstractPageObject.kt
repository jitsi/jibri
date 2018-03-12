package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.remote.RemoteWebDriver

/**
 * [AbstractPageObject] is a page object class containing logic common to
 * all page object instances
 */
open class AbstractPageObject(protected val driver: RemoteWebDriver) {
    open fun visit(callUrlInfo: CallUrlInfo): Boolean {
        driver.get(callUrlInfo.callUrl)
        return true
    }
}
