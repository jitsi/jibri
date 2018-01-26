package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory

/**
 * Page object representing the in-call page on a jitsi-meet server
 */
class CallPage(driver: WebDriver) : AbstractPageObject(driver)
{
    @FindBy(xpath = "//*[@id=\"toolbar_button_hangup\"]")
    private val hangUpButton: WebElement? = null

    init
    {
        PageFactory.initElements(driver, this)
    }

    fun leave()
    {
        hangUpButton?.click()
        //TODO: wait for call to be fully exited
    }
}