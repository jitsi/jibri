/*
 * Copyright @ 2026 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jitsi.jibri.api.xmpp

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.config.XmppMuc
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.helpers.resetScheduledPool
import org.jitsi.jibri.helpers.setScheduledPool
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.TaskPools
import org.jitsi.utils.concurrent.FakeScheduledExecutorService
import org.jitsi.utils.time.FakeClock
import org.jitsi.xmpp.mucclient.ConnectionStateListener
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientManager
import java.time.Duration

class XmppApiConnectionStateTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    init {
        val resumeTimeout = Duration.ofSeconds(20)
        val clock = FakeClock()
        val scheduler = FakeScheduledExecutorService(clock)

        val xmppConfig = XmppEnvironmentConfig(
            name = "xmppEnvName",
            xmppServerHosts = listOf("xmppServerHost1", "xmppServerHost2:6222"),
            xmppDomain = "xmppDomain",
            baseUrl = "baseUrl",
            controlLogin = XmppCredentials(domain = "controlXmppDomain", username = "u", password = "p"),
            controlMuc = XmppMuc(domain = "xmppMucDomain", roomName = "room", nickname = "nick"),
            sipControlMuc = null,
            callLogin = XmppCredentials(domain = "callXmppDomain", username = "u", password = "p"),
            stripFromRoomDomain = "",
            usageTimeoutMins = 0,
            trustAllXmppCerts = true
        )

        val jibriManager: JibriManager = mockk(relaxed = true)
        // The service was started through the first host.
        every { jibriManager.currentEnvironmentContext } returns EnvironmentContext("xmppEnvName-xmppServerHost1")

        val mucClient: MucClient = mockk(relaxed = true)
        every { mucClient.id } returns "xmppServerHost1"
        every { mucClient.isSmResumptionPossible } returns true
        val otherMucClient: MucClient = mockk(relaxed = true)
        every { otherMucClient.id } returns "xmppServerHost2:6222"
        every { otherMucClient.isSmResumptionPossible } returns true

        val listener = slot<ConnectionStateListener>()
        val mucClientManager: MucClientManager = mockk(relaxed = true)
        every { mucClientManager.addConnectionStateListener(capture(listener)) } answers { }

        beforeTest { TaskPools.setScheduledPool(scheduler) }
        afterTest { TaskPools.resetScheduledPool() }

        XmppApi(jibriManager, listOf(xmppConfig), mockk<JibriStatusManager>(relaxed = true), resumeTimeout)
            .start(mucClientManager)

        fun elapse(duration: Duration) {
            clock.elapse(duration)
            scheduler.run()
        }

        context("when the connection the service runs on drops and can resume") {
            listener.captured.closedOnError(mucClient)

            should("keep the service running while waiting for the connection") {
                elapse(resumeTimeout.minusSeconds(1))
                verify(exactly = 0) { jibriManager.stopService() }
            }
            context("and the session is resumed") {
                elapse(Duration.ofSeconds(5))
                listener.captured.authenticated(mucClient, true)
                should("keep the service running") {
                    elapse(resumeTimeout.multipliedBy(2))
                    verify(exactly = 0) { jibriManager.stopService() }
                    scheduler.numPendingJobs() shouldBe 0
                }
            }
            context("and the connection comes back with a new session") {
                elapse(Duration.ofSeconds(5))
                listener.captured.authenticated(mucClient, false)
                should("stop the service right away, and only once") {
                    verify(exactly = 1) { jibriManager.stopService() }
                    elapse(resumeTimeout.multipliedBy(2))
                    verify(exactly = 1) { jibriManager.stopService() }
                }
            }
            context("and the connection does not come back in time") {
                elapse(resumeTimeout)
                should("stop the service") {
                    verify(exactly = 1) { jibriManager.stopService() }
                }
            }
        }

        context("when the connection the service runs on drops and cannot resume") {
            every { mucClient.isSmResumptionPossible } returns false
            listener.captured.closedOnError(mucClient)
            should("stop the service right away") {
                verify(exactly = 1) { jibriManager.stopService() }
                scheduler.numPendingJobs() shouldBe 0
            }
        }

        context("when the connection the service runs on is closed cleanly") {
            listener.captured.closed(mucClient)
            should("stop the service right away") {
                verify(exactly = 1) { jibriManager.stopService() }
            }
        }

        context("when a closed connection was waiting to resume") {
            listener.captured.closedOnError(mucClient)
            listener.captured.closed(mucClient)
            should("stop the service once, without a stop left pending") {
                verify(exactly = 1) { jibriManager.stopService() }
                scheduler.numPendingJobs() shouldBe 0
            }
        }

        context("when another connection drops") {
            listener.captured.closedOnError(otherMucClient)
            listener.captured.authenticated(otherMucClient, false)
            should("leave the service alone") {
                elapse(resumeTimeout.multipliedBy(2))
                verify(exactly = 0) { jibriManager.stopService() }
                scheduler.numPendingJobs() shouldBe 0
            }
        }

        context("when a connection logs in for the first time") {
            every { jibriManager.currentEnvironmentContext } returns null
            listener.captured.authenticated(mucClient, false)
            should("not stop anything") {
                verify(exactly = 0) { jibriManager.stopService() }
            }
        }
    }
}
