package org.jitsi.jibri.selenium.status_checks

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.secs
import org.jitsi.utils.time.FakeClock

class IceConnectionStatusCheckTest : ShouldSpec() {
    private val clock: FakeClock = spyk()
    private val callPage: CallPage = mockk {
        every { isCallEmpty() } returns false
    }
    private val logger: Logger = mockk(relaxed = true)

    private val check = IceConnectionStatusCheck(logger, clock)

    init {
        context("When ICE connects") {
            every { callPage.isIceConnected() } returns true
            should("not report any event") {
                repeat(10) {
                    check.run(callPage) shouldBe null
                    clock.elapse(1.secs)
                }
            }
        }
        context("When ICE disconnects") {
            every { callPage.isIceConnected() } returns false
            should("not fire an event immediately") {
                repeat(10) {
                    check.run(callPage) shouldBe null
                    clock.elapse(1.secs)
                }
            }
            should("fire an event eventually") {
                clock.elapse(20.secs)
                check.run(callPage) shouldBe SeleniumEvent.IceFailedEvent
            }
        }
        context("When ICE disconnects but recovers") {
            every { callPage.isIceConnected() } returns true
            check.run(callPage) shouldBe null
            every { callPage.isIceConnected() } returns false
            should("not fire an event immediately") {
                repeat(10) {
                    check.run(callPage) shouldBe null
                    clock.elapse(1.secs)
                }
            }
            every { callPage.isIceConnected() } returns true
            should("not fire an event") {
                repeat(10) {
                    check.run(callPage) shouldBe null
                    clock.elapse(10.secs)
                }
            }
        }
    }
}
