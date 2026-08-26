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
 */

package org.jitsi.jibri.integration

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.selenium.RECORDING_URL_OPTIONS
import org.jitsi.jibri.selenium.pageobjects.AppCallPage
import org.jitsi.jibri.selenium.pageobjects.CallPage
import org.jitsi.jibri.selenium.pageobjects.ExternalAPIPage
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.RemoteWebDriver
import java.time.Duration
import java.util.logging.Level

/**
 * The two [CallPage] implementations jibri can join a conference with.  Every test runs against both, so the
 * two stay behaviourally interchangeable.
 */
enum class CallPageImpl(val create: (RemoteWebDriver) -> CallPage) {
    APP({ AppCallPage(it) }),
    EXTERNAL_API({ ExternalAPIPage(it) })
}

/**
 * A chrome instance driven by selenium the same way jibri drives it in production, joined to a conference
 * through one of the [CallPage] implementations.
 */
class JibriBrowser(impl: CallPageImpl) : AutoCloseable {
    private val driver: ChromeDriver = ChromeDriver(chromeOptions())
    val page: CallPage = impl.create(driver)

    init {
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60))
    }

    /** Joins [roomName] with the url params jibri uses when recording.  Returns whether the join succeeded. */
    fun join(roomName: String): Boolean =
        page.visit(CallUrlInfo(TestDeployment.baseUrl, roomName, urlParams = RECORDING_URL_OPTIONS))

    /**
     * The warnings and errors from the chrome console, which is where jitsi-meet reports why it could not
     * connect.  The full log is far too noisy to attach to a test failure.
     */
    fun browserLogs(): List<String> = try {
        driver.manage().logs().get(LogType.BROWSER).all
            .filter { it.level.intValue() >= Level.WARNING.intValue() }
            .takeLast(MAX_LOG_ENTRIES)
            .map { it.toString() }
    } catch (t: Throwable) {
        listOf("Could not read the browser log: $t")
    }

    override fun close() {
        driver.quit()
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 30

        fun chromeOptions() = ChromeOptions().apply {
            // ExternalAPIPage loads the recorder page from a file:// url, so chrome has to allow that page to
            // reach the deployment.
            addArguments("--allow-file-access-from-files")
            // The test deployment serves a self-signed certificate.
            setAcceptInsecureCerts(true)
            addArguments("--ignore-certificate-errors")
            addArguments("--use-fake-ui-for-media-stream")
            addArguments("--use-fake-device-for-media-stream")
            addArguments("--autoplay-policy=no-user-gesture-required")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--disable-gpu")
            addArguments("--window-size=1280,720")
            if (TestDeployment.headless) {
                addArguments("--headless=new")
            }
            setCapability(
                "goog:loggingPrefs",
                LoggingPreferences().apply { enable(LogType.BROWSER, Level.ALL) }
            )
        }
    }
}
