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

/** The disco feature a real jigasi advertises, and which jibri identifies jigasi participants by. */
const val JIGASI_FEATURE = "http://jitsi.org/protocol/jigasi"

/**
 * A regular jitsi-meet participant, driven with playwright.  These are the other people in the conference:
 * jibri itself always joins through a [org.jitsi.jibri.selenium.pageobjects.CallPage] instead, so that the
 * tests exercise jibri's own code.
 *
 * The first participant to join a conference becomes its moderator, which is what the scenarios that need
 * somebody to kick jibri or turn on AV moderation rely on.
 */
class MeetParticipant(
    val displayName: String,
    /**
     * The `context.user` of the JWT this participant joins with.  Without one the participant is anonymous
     * and carries no identity in its presence.  `hidden-from-recorder` set to "true" here is what makes a
     * participant invisible to a recording jibri.
     */
    private val userContext: Map<String, String>? = null
) : AutoCloseable {
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
        val token = userContext?.let { "?jwt=${TestDeployment.jwtFor(it)}" } ?: ""
        page.navigate("${TestDeployment.baseUrl}/$roomName$token#${urlParams.joinToString("&")}")
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

    /**
     * Advertises [feature] in this participant's disco#info, the way jigasi and the transcriber do.  Other
     * endpoints only query features when they see the participant join, so this has to happen before jibri
     * joins the conference.
     */
    fun advertiseFeature(feature: String) {
        // The third argument marks the feature "external", which is what puts it in the MUC presence.
        // Features are only read from presence, so without it nobody else ever sees it.
        page.evaluate("(feature) => APP.conference._room.xmpp.caps.addFeature(feature, true, true)", feature)
    }

    /**
     * Waits until this participant has been granted moderator rights.  Jicofo grants them a moment after
     * the conference is joined, and the moderator-only operations below are silently dropped before that.
     */
    fun awaitModerator(): MeetParticipant {
        page.waitForFunction(
            "() => APP.conference._room.isModerator()",
            null,
            Page.WaitForFunctionOptions().setTimeout(MODERATOR_TIMEOUT_MS)
        )
        return this
    }

    /** Kicks every remote participant.  Only a moderator can do this. */
    fun kickEveryoneElse() {
        awaitModerator()
        page.evaluate(
            """
            () => APP.conference._room.getParticipants()
                .forEach(p => APP.conference._room.kickParticipant(p.getId()))
            """.trimIndent()
        )
    }

    /** Turns AV moderation on or off for [mediaType] ("audio" or "video").  Only a moderator can do this. */
    fun setAvModerationEnabled(mediaType: String, enabled: Boolean) {
        awaitModerator()
        page.evaluate(
            """
            ([mediaType, enabled]) => enabled
                ? APP.conference._room.enableAVModeration(mediaType)
                : APP.conference._room.disableAVModeration(mediaType)
            """.trimIndent(),
            listOf(mediaType, enabled)
        )
    }

    /** Approves every remote participant to unmute [mediaType] while AV moderation is on. */
    fun approveEveryoneElse(mediaType: String) {
        awaitModerator()
        page.evaluate(
            """
            (mediaType) => APP.conference._room.getParticipants()
                .forEach(p => APP.conference._room.avModerationApprove(mediaType, p.getId()))
            """.trimIndent(),
            mediaType
        )
    }

    /** The remote participants this participant sees, one map per participant. */
    @Suppress("UNCHECKED_CAST")
    fun remoteParticipants(): List<Map<String, Any?>> {
        val result = page.evaluate(
            """
            () => APP.conference._room.getParticipants().map(p => {
                const stored = APP.store.getState()['features/base/participants'].remote.get(p.getId());
                return {
                    id: p.getId(),
                    displayName: p.getDisplayName(),
                    audioMuted: p.isAudioMuted(),
                    videoMuted: p.isVideoMuted(),
                    raisedHand: Number(stored?.raisedHandTimestamp || 0) > 0
                };
            })
            """.trimIndent()
        )
        return result as? List<Map<String, Any?>> ?: listOf()
    }

    /** The single remote participant, which in these tests is always jibri. */
    fun onlyRemoteParticipant(): Map<String, Any?> = remoteParticipants().single()

    /**
     * Reads a value jibri put in its presence with `addToPresence` or `setParticipantProperties`.  Those
     * become plain presence nodes rather than anything jitsi-meet models, so the raw last presence is
     * where they show up.  The name is the raw one: jibri asks the External API not to prefix them.
     */
    fun remotePresenceValue(key: String): String? = rawRemotePresenceValue(key)

    private fun rawRemotePresenceValue(tagName: String): String? = page.evaluate(
        """
        (tagName) => {
            const room = APP.conference._room.room;
            const remote = APP.conference._room.getParticipants()[0];
            if (!remote) return null;
            const nodes = room.getLastPresence(remote.getId()) || [];
            const node = nodes.find(n => n.tagName === tagName);
            if (!node) return null;
            return typeof node.value === 'string' ? node.value : JSON.stringify(node.value);
        }
        """.trimIndent(),
        tagName
    ) as? String

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
        const val MODERATOR_TIMEOUT_MS = 30_000.0
    }
}
