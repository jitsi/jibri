/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */

package org.jitsi.jibri.service.impl

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.error.JibriError
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.FailedToJoinCall
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.PjsuaClient
import org.jitsi.jibri.sipgateway.pjsua.util.RemoteSipClientBusy
import org.jitsi.jibri.status.ComponentState

internal class SipGatewayJibriServiceTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val callParams = CallParams(
        CallUrlInfo("baseUrl", "callName"),
        "someemail@jitsi.net"
    )
    private val callLoginParams = XmppCredentials(
        domain = "domain",
        username = "username",
        password = "password"
    )
    private val sipClientParams = SipClientParams(
        "sipAddress@sip.8x8.vc",
        "sipusername",
        true
    )

    private val sipGatewayServiceParams = SipGatewayServiceParams(
        callParams,
        callLoginParams,
        sipClientParams
    )

    private val seleniumMockHelper = SeleniumMockHelper()
    private val pjsuaClientMockHelper = PjsuaClientMockHelper()
    private val statusUpdates = mutableListOf<ComponentState>()
    private val sipGatewayJibriService =
        SipGatewayJibriService(sipGatewayServiceParams, seleniumMockHelper.mock, pjsuaClientMockHelper.mock).also {
            it.addStatusHandler(statusUpdates::add)
        }

    init {
        context("starting a sip gateway service") {
            sipGatewayJibriService.start()
            should("have selenium join the call") {
                verify { seleniumMockHelper.mock.joinCall(any(), any()) }
            }
            context("and selenium joins the call successfully") {
                seleniumMockHelper.startSuccessfully()
                should("start pjsua") {
                    verify { pjsuaClientMockHelper.mock.start() }
                }
                context("and the pjsua starts successfully") {
                    pjsuaClientMockHelper.startSuccessfully()

                    should("publish that it's running") {
                        statusUpdates shouldHaveSize 1
                        val status = statusUpdates.first()

                        status shouldBe ComponentState.Running
                    }
                }
                context("but pjsua fails to start") {
                    pjsuaClientMockHelper.error(RemoteSipClientBusy)

                    should("publish an error") {
                        statusUpdates shouldHaveSize 1
                        val status = statusUpdates.first()

                        status.shouldBeInstanceOf<ComponentState.Error>()
                    }
                }
            }

            context("but joining the call fails") {
                seleniumMockHelper.error(FailedToJoinCall)

                should("publish an error") {
                    statusUpdates shouldHaveSize 1
                    val status = statusUpdates.first()

                    status.shouldBeInstanceOf<ComponentState.Error>()
                }
            }
        }
        context("stopping a service which has successfully started") {
            // First get the service in a 'successful start' state.
            sipGatewayJibriService.start()
            seleniumMockHelper.startSuccessfully()
            pjsuaClientMockHelper.startSuccessfully()

            // Validate that it started
            statusUpdates shouldHaveSize 1
            val status = statusUpdates.first()
            status shouldBe ComponentState.Running

            // Stop the service
            sipGatewayJibriService.stop()
            should("tell selenium to leave the call") {
                verify { seleniumMockHelper.mock.leaveCallAndQuitBrowser() }
            }
        }
    }
}

private class SeleniumMockHelper {
    private val eventHandlers = mutableListOf<(ComponentState) -> Boolean>()

    val mock: JibriSelenium = mockk(relaxed = true) {
        every { addTemporaryHandler(capture(eventHandlers)) } just Runs
        every { addStatusHandler(captureLambda()) } answers {
            // This behavior mimics what's done in StatusPublisher#addStatusHandler
            eventHandlers.add {
                lambda<(ComponentState) -> Unit>().captured(it)
                true
            }
        }
    }

    fun startSuccessfully() {
        eventHandlers.forEach { it(ComponentState.Running) }
    }

    fun error(error: JibriError) {
        eventHandlers.forEach { it(ComponentState.Error(error)) }
    }
}

private class PjsuaClientMockHelper() {
    private val eventHandlers = mutableListOf<(ComponentState) -> Boolean>()

    val mock: PjsuaClient = mockk(relaxed = true) {
        every { addTemporaryHandler(capture(eventHandlers)) } just Runs
        every { addStatusHandler(captureLambda()) } answers {
            // This behavior mimics what's done in StatusPublisher#addStatusHandler
            eventHandlers.add {
                lambda<(ComponentState) -> Unit>().captured(it)
                true
            }
        }
    }

    fun startSuccessfully() {
        eventHandlers.forEach { it(ComponentState.Running) }
    }

    fun error(error: JibriError) {
        eventHandlers.forEach { it(ComponentState.Error(error)) }
    }
}
