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

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright

/**
 * The playwright instance shared by every [MeetParticipant].  Starting playwright spawns a node driver
 * process, which is slow enough that it is worth doing only once per test run.
 */
private object PlaywrightRuntime {
    val playwright: Playwright by lazy {
        Playwright.create().also { instance ->
            Runtime.getRuntime().addShutdownHook(Thread { instance.close() })
        }
    }
}

/**
 * A regular jitsi-meet participant, driven with playwright.  These are the other people in the conference:
 * jibri itself always joins through a [org.jitsi.jibri.selenium.pageobjects.CallPage] instead, so that the
 * tests exercise jibri's own code.
 */
class MeetParticipant(val displayName: String) : AutoCloseable {
    private val browser: Browser = PlaywrightRuntime.playwright.chromium().launch(
        BrowserType.LaunchOptions()
            .setHeadless(TestDeployment.headless)
            .setArgs(
                listOf(
                    "--use-fake-ui-for-media-stream",
                    "--use-fake-device-for-media-stream",
                    "--autoplay-policy=no-user-gesture-required",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--ignore-certificate-errors"
                )
            )
    )
    private val context = browser.newContext(
        Browser.NewContextOptions()
            .setPermissions(listOf("camera", "microphone"))
            // The test deployment serves a self-signed certificate.
            .setIgnoreHTTPSErrors(true)
    )
    private val page: Page = context.newPage()

    /** Joins [roomName] and waits until the conference has actually been joined. */
    fun join(roomName: String): MeetParticipant {
        // Note that the prejoin page is turned off by the deployment rather than here: overriding
        // prejoinConfig.enabled from the url makes jitsi-meet start without media, and these participants
        // are the only source of media in the conference.
        val urlParams = listOf(
            "config.requireDisplayName=false",
            "config.deeplinking.disabled=true",
            "config.analytics.disabled=true",
            "config.p2p.enabled=false",
            "config.startWithAudioMuted=false",
            "config.startWithVideoMuted=false",
            "userInfo.displayName=\"$displayName\""
        )
        page.navigate("${TestDeployment.baseUrl}/$roomName#${urlParams.joinToString("&")}")
        page.waitForFunction(
            "() => Boolean(window.APP?.conference?._room?.isJoined())",
            null,
            Page.WaitForFunctionOptions().setTimeout(JOIN_TIMEOUT_MS)
        )
        return this
    }

    /** Mutes or unmutes both audio and video. */
    fun setMuted(muted: Boolean) {
        page.evaluate(
            """
            (muted) => {
                APP.conference.muteAudio(muted);
                APP.conference.muteVideo(muted);
            }
            """.trimIndent(),
            muted
        )
    }

    /** Leaves the conference, waiting for the XMPP leave to complete. */
    fun leave() {
        page.evaluate("() => APP.conference._room.leave()")
    }

    override fun close() {
        // Leave explicitly rather than relying on tearing the browser down: killing the browser leaves
        // prosody waiting for the websocket to time out, during which everyone else still sees the
        // participant in the room.
        runCatching { leave() }
        runCatching { context.close() }
        runCatching { browser.close() }
    }

    private companion object {
        const val JOIN_TIMEOUT_MS = 60_000.0
    }
}
