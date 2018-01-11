package org.jitsi.selenium.pageobjects

import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory

class CallPage(private val driver: WebDriver)
{
    @FindBy(xpath = "//*[@id=\"toolbar_button_hangup\"]")
    private val hangUpButton: WebElement? = null

    init
    {
        PageFactory.initElements(driver, this)
    }

    fun visit(baseDomain: String, callName: String)
    {
        driver.get(baseDomain + "/" + callName)
    }

    fun leave()
    {
        hangUpButton?.click()
        //TODO: wait for call to be fully exited
    }
}