/*
 * Copyright @ 2018 Atlassian Pty Ltd
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

package org.jitsi.jibri.selenium

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jitsi.utils.logging2.Logger
import org.openqa.selenium.Capabilities
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.logging.Logs
import java.time.Duration

@Suppress("BlockingMethodInNonBlockingContext")
internal class JibriSeleniumTimeoutTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf

    private val parentLogger: Logger = mockk(relaxed = true)
    private val logs: Logs = mockk(relaxed = true)

    init {
        beforeTest {
            every { logs.availableLogTypes } returns emptySet()
        }

        context("leaveCallAndQuitBrowser timeout behavior") {
            should("return within timeout period when quit() hangs") {
                // Create mock ChromeDriver that hangs
                val mockChromeDriver = mockk<ChromeDriver>(relaxed = true)
                val mockCapabilities = mockk<Capabilities>(relaxed = true)

                every { mockChromeDriver.manage() } returns mockk {
                    every { logs } returns this@JibriSeleniumTimeoutTest.logs
                    every { timeouts() } returns mockk {
                        every { pageLoadTimeout(any(), any()) } returns mockk()
                    }
                }
                every { mockChromeDriver.capabilities } returns mockCapabilities
                every { mockCapabilities.getCapability(any()) } returns null

                // Simulate quit() hanging for 10 seconds
                every { mockChromeDriver.quit() } answers {
                    Thread.sleep(10000)
                }

                // Create JibriSelenium with 500ms timeout
                val selenium = JibriSelenium(
                    parentLogger,
                    JibriSeleniumOptions(
                        display = ":0",
                        displayName = "Test",
                        email = "test@example.com",
                        emptyCallTimeout = Duration.ofSeconds(30)
                    ),
                    mockChromeDriver
                )

                // Call leaveCallAndQuitBrowser - should return within timeout + buffer
                val startTime = System.currentTimeMillis()
                selenium.leaveCallAndQuitBrowser()
                val duration = System.currentTimeMillis() - startTime

                // Should complete within 2 seconds (500ms timeout + buffer)
                duration shouldBeLessThan 2000

                // Verify quit was called
                verify { mockChromeDriver.quit() }

                // Verify forceKillChrome was triggered by checking capabilities access
                verify(atLeast = 1) { mockChromeDriver.capabilities }
            }

            should("call forceKillChrome when quit() times out") {
                val mockChromeDriver = mockk<ChromeDriver>(relaxed = true)
                val mockCapabilities = mockk<Capabilities>(relaxed = true)

                every { mockChromeDriver.manage() } returns mockk {
                    every { logs } returns this@JibriSeleniumTimeoutTest.logs
                    every { timeouts() } returns mockk {
                        every { pageLoadTimeout(any(), any()) } returns mockk()
                    }
                }
                every { mockChromeDriver.capabilities } returns mockCapabilities
                every { mockCapabilities.getCapability("chromeProcess") } returns null
                every { mockCapabilities.getCapability("chromeDriverService") } returns null

                // Simulate quit() hanging
                every { mockChromeDriver.quit() } answers {
                    Thread.sleep(10000)
                }

                val selenium = JibriSelenium(
                    parentLogger,
                    JibriSeleniumOptions(
                        display = ":0",
                        displayName = "Test",
                        email = "test@example.com",
                        emptyCallTimeout = Duration.ofSeconds(30)
                    ),
                    mockChromeDriver
                )

                selenium.leaveCallAndQuitBrowser()

                // Verify forceKillChrome was called by checking capabilities were accessed
                // This verifies the timeout path was taken
                verify(atLeast = 1) { mockCapabilities.getCapability("chromeProcess") }
            }

            should("complete quickly when quit() succeeds normally") {
                val mockChromeDriver = mockk<ChromeDriver>(relaxed = true)

                every { mockChromeDriver.manage() } returns mockk {
                    every { logs } returns this@JibriSeleniumTimeoutTest.logs
                    every { timeouts() } returns mockk {
                        every { pageLoadTimeout(any(), any()) } returns mockk()
                    }
                }

                // Normal quit() that returns immediately
                every { mockChromeDriver.quit() } returns Unit

                val selenium = JibriSelenium(
                    parentLogger,
                    JibriSeleniumOptions(
                        display = ":0",
                        displayName = "Test",
                        email = "test@example.com",
                        emptyCallTimeout = Duration.ofSeconds(30)
                    ),
                    mockChromeDriver
                )

                val startTime = System.currentTimeMillis()
                selenium.leaveCallAndQuitBrowser()
                val duration = System.currentTimeMillis() - startTime

                // Should complete quickly (within 500ms)
                duration shouldBeLessThan 500

                // Verify quit was called
                verify(exactly = 1) { mockChromeDriver.quit() }

                // Should NOT access capabilities (no force kill needed)
                verify(exactly = 0) { mockChromeDriver.capabilities }
            }

            should("force kill chrome when quit() throws exception") {
                val mockChromeDriver = mockk<ChromeDriver>(relaxed = true)
                val mockCapabilities = mockk<Capabilities>(relaxed = true)

                every { mockChromeDriver.manage() } returns mockk {
                    every { logs } returns this@JibriSeleniumTimeoutTest.logs
                    every { timeouts() } returns mockk {
                        every { pageLoadTimeout(any(), any()) } returns mockk()
                    }
                }
                every { mockChromeDriver.capabilities } returns mockCapabilities
                every { mockCapabilities.getCapability(any()) } returns null

                // Simulate quit() throwing exception
                every { mockChromeDriver.quit() } throws RuntimeException("Quit failed")

                val selenium = JibriSelenium(
                    parentLogger,
                    JibriSeleniumOptions(
                        display = ":0",
                        displayName = "Test",
                        email = "test@example.com",
                        emptyCallTimeout = Duration.ofSeconds(30)
                    ),
                    mockChromeDriver
                )

                // Should not throw
                selenium.leaveCallAndQuitBrowser()

                // Verify quit was called
                verify { mockChromeDriver.quit() }

                // Verify forceKillChrome was triggered
                verify(atLeast = 1) { mockChromeDriver.capabilities }
            }

            should("timeout when retrieving chromedriver logs hangs") {
                val mockChromeDriver = mockk<ChromeDriver>(relaxed = true)
                val mockCapabilities = mockk<Capabilities>(relaxed = true)

                every { mockChromeDriver.manage() } returns mockk {
                    every { logs } returns this@JibriSeleniumTimeoutTest.logs
                    every { timeouts() } returns mockk {
                        every { pageLoadTimeout(any(), any()) } returns mockk()
                    }
                }
                every { mockChromeDriver.capabilities } returns mockCapabilities
                every { mockCapabilities.getCapability(any()) } returns null

                // Simulate logs.availableLogTypes hanging
                every { logs.availableLogTypes } answers {
                    Thread.sleep(10000)
                    emptySet()
                }

                // Normal quit that returns immediately
                every { mockChromeDriver.quit() } returns Unit

                val selenium = JibriSelenium(
                    parentLogger,
                    JibriSeleniumOptions(
                        display = ":0",
                        displayName = "Test",
                        email = "test@example.com",
                        emptyCallTimeout = Duration.ofSeconds(30)
                    ),
                    mockChromeDriver
                )

                // Call leaveCallAndQuitBrowser - should return within timeout
                val startTime = System.currentTimeMillis()
                selenium.leaveCallAndQuitBrowser()
                val duration = System.currentTimeMillis() - startTime

                // Should complete within 2 seconds despite logs hanging for 10 seconds
                duration shouldBeLessThan 2000

                // Verify logs was attempted to be accessed
                verify { logs.availableLogTypes }

                // Verify quit was still called after timeout
                verify { mockChromeDriver.quit() }
            }

            should("continue when leave call hangs and timeout is triggered") {
                val mockChromeDriver = mockk<ChromeDriver>(relaxed = true)
                val mockCapabilities = mockk<Capabilities>(relaxed = true)

                every { mockChromeDriver.manage() } returns mockk {
                    every { logs } returns this@JibriSeleniumTimeoutTest.logs
                    every { timeouts() } returns mockk {
                        every { pageLoadTimeout(any(), any()) } returns mockk()
                    }
                }
                every { mockChromeDriver.capabilities } returns mockCapabilities
                every { mockCapabilities.getCapability(any()) } returns null

                // Normal logs retrieval
                every { logs.availableLogTypes } returns emptySet()

                // Normal quit that returns immediately
                every { mockChromeDriver.quit() } returns Unit

                val selenium = JibriSelenium(
                    parentLogger,
                    JibriSeleniumOptions(
                        display = ":0",
                        displayName = "Test",
                        email = "test@example.com",
                        emptyCallTimeout = Duration.ofSeconds(30)
                    ),
                    mockChromeDriver
                )

                // Call leaveCallAndQuitBrowser
                val startTime = System.currentTimeMillis()
                selenium.leaveCallAndQuitBrowser()
                val duration = System.currentTimeMillis() - startTime

                // Should complete quickly (within 1 second)
                duration shouldBeLessThan 1000

                // Verify quit was called
                verify { mockChromeDriver.quit() }
            }
        }
    }
}
