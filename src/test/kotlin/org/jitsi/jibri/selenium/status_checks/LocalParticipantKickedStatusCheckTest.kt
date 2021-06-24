package org.jitsi.jibri.selenium.status_checks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.utils.logging2.Logger

class LocalParticipantKickedStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val callPage: CallPage = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private val check = LocalParticipantKickedStatusCheck(logger)

    init {
        context("when local participant is kicked") {
            every { callPage.isLocalParticipantKicked() } returns true
            context("the check") {
                should("return LocalParticipantKicked immediately") {
                    check.run(callPage) shouldBe SeleniumEvent.LocalParticipantKicked
                }
            }
        }
        context("when local participant is not kicked") {
            every { callPage.isLocalParticipantKicked() } returns false
            context("the check") {
                should("returns null state") {
                    check.run(callPage) shouldBe null
                }
            }
        }
    }
}
