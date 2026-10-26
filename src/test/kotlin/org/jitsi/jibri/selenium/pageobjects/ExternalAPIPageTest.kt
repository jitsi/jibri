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
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.jxmpp.jid.parts.Localpart
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

        should("return true when the joined room is the authorized room") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true
            every { driver.executeScript("return window.jibriPageState?.roomName;") } returns "testroom"

            page.visit(callUrl.copy(authorizedRoom = Localpart.from("testroom"))) shouldBe true
        }

        should("return false when the joined room is not the authorized room") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true
            every { driver.executeScript("return window.jibriPageState?.roomName;") } returns "myroom"

            page.visit(callUrl.copy(callName = "myroom?x", authorizedRoom = Localpart.from("myroom?x"))) shouldBe false
            verify { driver.executeScript(match<String> { it.contains("'hangup'") }, *anyVararg<Any>()) }
        }

        should("return false when the joined room cannot be read") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true
            every { driver.executeScript("return window.jibriPageState?.roomName;") } returns null

            page.visit(callUrl.copy(authorizedRoom = Localpart.from("testroom"))) shouldBe false
            verify { driver.executeScript(match<String> { it.contains("'hangup'") }, *anyVararg<Any>()) }
        }

        should("ignore case when it compares the joined room with the authorized room") {
            every { driver.get(any<String>()) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true
            every { driver.executeScript("return window.jibriPageState?.roomName;") } returns "TestRoom"

            page.visit(callUrl.copy(authorizedRoom = Localpart.from("testroom"))) shouldBe true
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

        should("derive tenant from baseUrl path when tenant is not explicitly set") {
            // The HTTP startService API only sets baseUrl/callName, leaving tenant empty
            // even when baseUrl's path carries the tenant segment.
            val urlSlot = slot<String>()
            every { driver.get(capture(urlSlot)) } returns Unit
            every { driver.executeScript("return window.jibriPageState?.apiError;") } returns null
            every { driver.executeScript("return window.jibriPageState?.conferenceJoined === true;") } returns true

            page.visit(CallUrlInfo("https://meet.example.com/tenant-test", "room-test", "")) shouldBe true
            urlSlot.captured.shouldContain("room=room-test")
            urlSlot.captured.shouldContain("tenant=tenant-test")
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
            every { driver.executeAsyncScript(any<String>()) } returns 3L

            page.getNumParticipants() shouldBe 3
        }

        should("return true when only recorder present") {
            every { driver.executeAsyncScript(any<String>()) } returns 1L

            page.isCallEmpty() shouldBe true
        }

        should("return false when participants present") {
            every { driver.executeAsyncScript(any<String>()) } returns 2L

            page.isCallEmpty() shouldBe false
        }

        should("return number of jigasi participants") {
            every { driver.executeAsyncScript(any<String>()) } returns 2L

            page.numRemoteParticipantsJigasi() shouldBe 2
        }

        should("return 0 jigasi participants when script fails") {
            every { driver.executeAsyncScript(any<String>()) } throws RuntimeException("script error")

            page.numRemoteParticipantsJigasi() shouldBe 0
        }
    }
}
