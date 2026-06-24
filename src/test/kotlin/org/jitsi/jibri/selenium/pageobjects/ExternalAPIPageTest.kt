package org.jitsi.jibri.selenium.pageobjects

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.remote.RemoteWebDriver

internal class ExternalAPIPageTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        val driver = mockk<RemoteWebDriver>(relaxed = true)
        val page = ExternalAPIPage(driver)
        val callUrl = CallUrlInfo("https://meet.example.com", "testroom")

        should("return true when conference joined") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript(any<String>()) } returns true

            page.visit(callUrl) shouldBe true
        }

        should("return false on timeout when conference never joins") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript(any<String>()) } returns false

            page.visit(callUrl) shouldBe false
        }

        should("return false if exception occurs") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript(any<String>()) } throws RuntimeException("Script error")

            page.visit(callUrl) shouldBe false
        }

        should("return participant count") {
            every { driver.executeScript(any<String>()) } returns 3L

            page.getNumParticipants() shouldBe 3
        }

        should("return true when only recorder present") {
            every { driver.executeScript(any<String>()) } returns 1L

            page.isCallEmpty() shouldBe true
        }

        should("return false when participants present") {
            every { driver.executeScript(any<String>()) } returns 2L

            page.isCallEmpty() shouldBe false
        }
    }
}
