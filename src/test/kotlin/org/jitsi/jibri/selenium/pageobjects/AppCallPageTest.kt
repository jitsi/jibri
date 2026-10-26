package org.jitsi.jibri.selenium.pageobjects

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.remote.RemoteWebDriver

internal class AppCallPageTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        val driver = mockk<RemoteWebDriver>(relaxed = true)
        val page = AppCallPage(driver)
        val callUrl = CallUrlInfo("https://meet.example.com", "testroom", "", authorizedRoom = "testroom")

        every { driver.executeScript(match<String> { it.contains("isJoined()") }) } returns true
        every { driver.executeScript(match<String> { it.contains("getParticipants()") }) } returns 1L

        should("return true when the joined room is the authorized room") {
            every { driver.executeScript(match<String> { it.contains("roomjid") }) } returns
                mapOf("roomJid" to "TestRoom@conference.meet.example.com")

            page.visit(callUrl) shouldBe true
            verify(exactly = 0) { driver.executeScript(match<String> { it.contains("_room.leave()") }) }
        }

        should("leave and return false when the joined room is not the authorized room") {
            every { driver.executeScript(match<String> { it.contains("roomjid") }) } returns
                mapOf("roomJid" to "victim@conference.meet.example.com")

            page.visit(callUrl) shouldBe false
            verify { driver.executeScript(match<String> { it.contains("_room.leave()") }) }
        }

        should("leave and return false when the joined room cannot be read") {
            every { driver.executeScript(match<String> { it.contains("roomjid") }) } returns
                mapOf("error" to "Cannot read properties of undefined")

            page.visit(callUrl) shouldBe false
            verify { driver.executeScript(match<String> { it.contains("_room.leave()") }) }
        }

        should("return false when other participants stay in the room after leaving") {
            every { driver.executeScript(match<String> { it.contains("roomjid") }) } returns
                mapOf("roomJid" to "victim@conference.meet.example.com")
            every { driver.executeScript(match<String> { it.contains("_room.leave()") }) } returns null
            every { driver.executeScript(match<String> { it.contains("getParticipants()") }) } returns 2L

            page.visit(callUrl) shouldBe false
        }

        should("not check the room when no room is authorized") {
            page.visit(callUrl.copy(authorizedRoom = null)) shouldBe true
            verify(exactly = 0) { driver.executeScript(match<String> { it.contains("roomjid") }) }
        }
    }
}
