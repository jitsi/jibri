package org.jitsi.jibri.selenium.pageobjects

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.remote.RemoteWebDriver

internal class ExternalAPIPageTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        val driver = mockk<RemoteWebDriver>(relaxed = true)
        val page = ExternalAPIPage(driver)
        val callUrl = CallUrlInfo("https://meet.example.com", "testroom")

        should("return true when visiting call URL") {
            every { driver.get(any<String>()) } returns Unit
            page.visit(callUrl) shouldBe true
            verify { driver.get(any()) }
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
