package org.jitsi.jibri.selenium.util

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

/**
 * Utility class.
 * @author Damian Minkov
 * @author Pawel Domas
 */
object SeleniumUtils {
    var IS_LINUX: Boolean = false

    var IS_MAC: Boolean = false

    init {
        // OS
        val osName: String? = System.getProperty("os.name")

        when {
            osName == null -> {
                IS_LINUX = false
                IS_MAC = false
            }
            osName.startsWith(prefix = "Linux") -> {
                IS_LINUX = true
                IS_MAC = false
            }
            osName.startsWith(prefix = "Mac") -> {
                IS_LINUX = false
                IS_MAC = true
            }
            else -> {
                IS_LINUX = false
                IS_MAC = false
            }
        }
    }

    /**
     * Click an element on the page by first checking for visibility and then
     * checking for clickability.
     *
     * @param driver the `WebDriver`.
     * @param by the search query for the element
     */
    fun click(driver: WebDriver, by: By) {
        waitForElementBy(driver = driver, by = by, timeout = 10)
        val wait = WebDriverWait(driver, 10)
        val element: WebElement = wait.until(ExpectedConditions.elementToBeClickable(by))
        element.click()
    }

    /**
     * Waits until an element becomes available and return it.
     * @param driver the `WebDriver`.
     * @param by the xpath to search for the element
     * @param timeout the time to wait for the element in seconds.
     * @return WebElement the found element
     */
    private fun waitForElementBy(driver: WebDriver, by: By, timeout: Long): WebElement? {
        var foundElement: WebElement? = null
        WebDriverWait(driver, timeout)
            .until<Boolean>(ExpectedCondition<Boolean?> { d: WebDriver? ->
                val elements: List<WebElement> = d!!.findElements(by)
                when {
                    elements.isNotEmpty() -> {
                        foundElement = elements[0]
                        return@ExpectedCondition true
                    }
                    else -> return@ExpectedCondition false
                }
            })

        return foundElement
    }
}
