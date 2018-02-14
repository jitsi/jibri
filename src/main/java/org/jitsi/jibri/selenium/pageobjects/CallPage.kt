package org.jitsi.jibri.selenium.pageobjects

import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory

/**
 * Page object representing the in-call page on a jitsi-meet server
 */
class CallPage(driver: WebDriver) : AbstractPageObject(driver) {
    @FindBy(xpath = "//*[@id=\"toolbar_button_hangup\"]")
    private val hangUpButton: WebElement? = null

    init {
        PageFactory.initElements(driver, this)
    }

    fun getNumParticipants(driver: RemoteWebDriver): Long {
        val result = driver.executeScript("""
            try {
                return APP.conference.membersCount
            } catch (e) {
                return e.message;
            }
        """.trimMargin())
        return when (result) {
            is Long -> result
            else -> 1
        }
    }

    fun leave() {
        hangUpButton?.click()
        //TODO: wait for call to be fully exited
    }
}