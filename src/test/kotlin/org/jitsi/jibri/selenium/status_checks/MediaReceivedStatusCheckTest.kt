package org.jitsi.jibri.selenium.status_checks

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.helpers.FakeClock
import org.jitsi.jibri.selenium.SeleniumEvent
import org.jitsi.jibri.selenium.pageobjects.CallPage
import java.time.Duration
import java.util.logging.Logger

class MediaReceivedStatusCheckTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val clock: FakeClock = spy()
    private val callPage: CallPage = mock()
    private val logger: Logger = mock()

    private val check = MediaReceivedStatusCheck(logger, clock)

    init {
        whenever(callPage.getNumParticipants()).thenReturn(4)
        context("when media is flowing") {
            whenever(callPage.getBitrates()).thenReturn(mapOf(
                "download" to 1024L
            ))
            whenever(callPage.numRemoteParticipantsMuted()).thenReturn(0)
            should("not report any event") {
                repeat(10) {
                    check.run(callPage) shouldBe null
                    clock.elapse(Duration.ofSeconds(30))
                }
            }
        }
        context("no media is flowing") {
            whenever(callPage.getBitrates()).thenReturn(mapOf(
                "download" to 0L
            ))
            context("but all participants are muted") {
                whenever(callPage.numRemoteParticipantsMuted()).thenReturn(3)
                check.run(callPage) shouldBe null
                context("before the clients-muted timeout") {
                    clock.elapse(Duration.ofSeconds(45))
                    should("not report any event") {
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
                whenever(callPage.numRemoteParticipantsMuted()).thenReturn(0)
                context("before the no-media timeout") {
                    clock.elapse(Duration.ofSeconds(15))
                    should("not report any event") {
                        check.run(callPage) shouldBe null
                    }
                    context("and media starts back up before the no-media timeout") {
                        whenever(callPage.getBitrates()).thenReturn(mapOf(
                                "download" to 1024L
                        ))
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
