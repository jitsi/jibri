package org.jitsi.jibri.selenium.status_checks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.jitsi.jibri.helpers.FakeClock
import org.jitsi.jibri.helpers.seconds
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Duration
import java.util.logging.Logger

class MediaReceivedStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val clock: FakeClock = spyk()
    private val callPage: CallPage = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private val check = MediaReceivedStatusCheck(logger, clock)

    init {
        every { callPage.getNumParticipants() } returns 4
        context("when media is flowing") {
            every { callPage.getBitrates() } returns mapOf("download" to 1024L)
            every { callPage.numRemoteParticipantsMuted() } returns 0
            should("not report any event") {
                repeat(10) {
                    check.run(callPage) shouldBe null
                    clock.elapse(Duration.ofSeconds(30))
                }
            }
        }
        context("no media is flowing") {
            every { callPage.getBitrates() } returns mapOf("download" to 0L)
            context("but all participants are muted") {
                every { callPage.numRemoteParticipantsMuted() } returns 3
                check.run(callPage) shouldBe null
                context("before the clients-muted timeout") {
                    clock.elapse(Duration.ofSeconds(45))
                    should("not report any event") {
                        check.run(callPage) shouldBe null
                    }
                }
                context("and then when clients unmute after the no-media timeout has elapsed") {
                    // Go past NO_MEDIA_TIMEOUT, but everyone still muted
                    clock.elapse(35.seconds)
                    every { callPage.numRemoteParticipantsMuted() } returns 3
                    check.run(callPage)
                    // Now go another second, but none are muted
                    clock.elapse(1.seconds)
                    every { callPage.numRemoteParticipantsMuted() } returns 0
                    should("wait for the full no-media timeout duration before dropping") {
                        check.run(callPage) shouldBe null
                    }
                }
                context("after the clients-muted timeout") {
                    clock.elapse(Duration.ofMinutes(15))
                    should("report an empty call") {
                        check.run(callPage) shouldBe SeleniumEvent.CallEmpty
                    }
                }
            }
            context("and not all participants are muted") {
                every { callPage.numRemoteParticipantsMuted() } returns 0
                context("before the no-media timeout") {
                    clock.elapse(Duration.ofSeconds(15))
                    should("not report any event") {
                        check.run(callPage) shouldBe null
                    }
                    context("and media starts back up before the no-media timeout") {
                        every { callPage.getBitrates() } returns mapOf("download" to 1024L)
                        clock.elapse(Duration.ofMinutes(1))
                        should("not report any event") {
                            check.run(callPage) shouldBe null
                        }
                    }
                }
                context("after the no-media timeout") {
                    clock.elapse(Duration.ofSeconds(45))
                    should("report a no media error") {
                        check.run(callPage) shouldBe SeleniumEvent.NoMediaReceived
                    }
                }
            }
        }
    }
}
