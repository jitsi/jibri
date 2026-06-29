package org.jitsi.jibri.selenium.pageobjects

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.jitsi.jibri.CallUrlInfo
import org.openqa.selenium.remote.RemoteWebDriver

internal class ExternalAPIPageTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        val driver = mockk<RemoteWebDriver>(relaxed = true)
        val page = ExternalAPIPage(driver)
        val callUrl = CallUrlInfo("https://meet.example.com", "testroom", "")

        should("return true when conference joined") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true

            page.visit(callUrl) shouldBe true
        }

        should("return false when conference not joined") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns false

            page.visit(callUrl) shouldBe false
        }

        should("extract room name and pass tenant from URL path") {
            val t = table(
                headers("input", "room", "tenant"),
                row("roomname", "roomname", ""),
                row("tenant/roomname", "roomname", "tenant"),
                row("tenant/abc/roomname", "roomname", "abc"),
                row("org/tenant/abc/test", "test", "abc")
            )
            forAll(t) { input, room, tenant ->
                val urlSlot = slot<String>()
                every { driver.get(capture(urlSlot)) } returns Unit
                every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
                every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true

                page.visit(CallUrlInfo("https://meet.example.com", input, tenant)) shouldBe true
                urlSlot.captured.shouldContain("room=$room")
                if (tenant.isNotEmpty()) {
                    urlSlot.captured.shouldContain("tenant=$tenant")
                }
            }
        }

        should("return false if apiError occurs") {
            val t = table(
                headers("errorAtCall", "scenario"),
                row(1, "at initialization"),
                row(4, "during polling after 3 cycles")
            )
            forAll(t) { errorAtCall, scenario ->
                every { driver.get(any<String>()) } returns Unit
                var callCount = 0
                every { driver.executeScript("return window.jibriPageState?.apiError;") } answers {
                    callCount++
                    if (callCount >= errorAtCall) "API error" else null
                }
                every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns false

                page.visit(callUrl) shouldBe false
                callCount shouldBe errorAtCall
            }
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
