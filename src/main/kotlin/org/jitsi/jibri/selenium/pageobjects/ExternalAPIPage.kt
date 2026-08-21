package org.jitsi.jibri.selenium.pageobjects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/** CallPage implementation with External API event-driven updates. */
class ExternalAPIPage(driver: RemoteWebDriver) : AbstractPageObject(driver), CallPage {
    private val logger = createLogger()
    private val mapper by lazy { jacksonObjectMapper() }

    /**
     * The bundled recorder.html, extracted to a local file so we can load it
     * directly in the browser instead of relying on the deployment to serve it.
     * Extracted lazily and reused for the lifetime of the page object.
     */
    private val recorderHtmlFile: File by lazy { extractRecorderHtml() }

    init {
        PageFactory.initElements(driver, this)
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20))
    }

    override fun visit(url: CallUrlInfo): Boolean {
        val room = url.callName.substringAfterLast('/').lowercase()
        // The deployment base url is passed to the page so it can source
        // external_api.js from the deployment (the page itself is loaded from a
        // local file and has no deployment origin of its own).
        val baseUrl = url.baseUrl
        // `tenant` is only set explicitly for XMPP-invited joins; other callers (e.g. the
        // HTTP startService API) only set baseUrl/callName, so fall back to baseUrl's path.
        val tenantPath = if (url.tenant.isNotEmpty()) {
            url.tenant.replace(".", "/")
        } else {
            URI(baseUrl).path.trim('/')
        }
        val recorderUrl = buildString {
            append(recorderHtmlFile.toURI().toString())
            append("?room=").append(encode(room))
            append("&baseUrl=").append(encode(baseUrl))
            if (tenantPath.isNotEmpty()) {
                append("&tenant=").append(encode(tenantPath))
            }
            if (url.urlParams.isNotEmpty()) {
                val parsedConfig = parseUrlParams(url.urlParams)
                append("&config=").append(encode(mapper.writeValueAsString(parsedConfig)))
            }
        }

        logger.info("Loading recorder page for room=$room baseUrl=$baseUrl tenant=$tenantPath")
        driver.get(recorderUrl)

        return try {
            WebDriverWait(driver, Duration.ofSeconds(30)).until {
                val apiError = driver.executeScript("return window.jibriPageState?.apiError;")
                if (apiError != null) {
                    throw IllegalStateException("API error: $apiError")
                }
                val script = "return window.jibriPageState?.conferenceJoined === true;"
                val result = driver.executeScript(script) as? Boolean ?: false
                result
            }
            logger.info("Recorder page initialized successfully")
            true
        } catch (e: TimeoutException) {
            logger.error("Failed to join conference: timeout waiting for conferenceJoined event")
            false
        } catch (e: IllegalStateException) {
            logger.error("Failed to join conference: ${e.message}")
            false
        }
    }

    private fun extractRecorderHtml(): File {
        val tmpFile = File.createTempFile("jibri-recorder", ".html").apply { deleteOnExit() }
        val resource = javaClass.getResourceAsStream("/recorder.html")
            ?: throw IllegalStateException("recorder.html not found on the classpath")
        resource.use { input -> tmpFile.outputStream().use { input.copyTo(it) } }
        return tmpFile
    }

    // Parses "config.a.b=value" strings into nested {config: {a: {b: value}}} objects.
    private fun parseUrlParams(rawParams: List<String>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        for (param in rawParams) {
            if (param.isEmpty()) continue

            val parts = param.split("=", limit = 2)
            if (parts.size != 2) continue

            val (key, rawValue) = parts
            if (key.isEmpty() || rawValue.isEmpty()) continue

            val value = try {
                mapper.readValue(rawValue, Any::class.java)
            } catch (e: Exception) {
                rawValue
            }

            setNestedValue(result, key, value)
        }

        return result
    }

    // Recursively build nested map from dotted path: "config.a.b" → map[config][a][b] = value
    @Suppress("UNCHECKED_CAST")
    private fun setNestedValue(map: MutableMap<String, Any?>, path: String, value: Any?) {
        val parts = path.split(".")
        var current = map
        for (i in 0 until parts.size - 1) {
            current = (current.getOrPut(parts[i]) { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>)
        }
        current[parts.last()] = value
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    /**
     * Calls [methodName] on window.jibriRecorderApi, guarding against the API or the method
     * not being ready yet and any error. [script] can assume `api` and `cb` are already in scope.
     */
    private fun callRecorderApiAsync(methodName: String, script: String): Any? {
        return try {
            driver.executeAsyncScript(
                """
                const cb = arguments[arguments.length - 1];
                const api = window.jibriRecorderApi;
                if (!api || typeof api.$methodName !== 'function') {
                    cb(null);
                    return;
                }
                $script
                """
            )
        } catch (t: Throwable) {
            logger.error("Error calling jibriRecorderApi.$methodName", t)
            null
        }
    }

    /** Enable the local camera and microphone. Used in sip gateway mode. */
    override fun unmute(): Boolean {
        val audioOk = unmuteMedia("audio", "isAudioMuted", "toggleAudio")
        val videoOk = unmuteMedia("video", "isVideoMuted", "toggleVideo")
        return audioOk && videoOk
    }

    private fun unmuteMedia(mediaType: String, isMutedMethod: String, toggleCommand: String): Boolean {
        val result = toggleMute(mediaType, isMutedMethod, toggleCommand, requiredInitialState = true)
        val success = result is String && result.startsWith("ok")
        if (!success) {
            logger.error("Failed to unmute $mediaType: $result")
        }
        return success
    }

    private fun executeRecorderCommand(command: String, vararg args: Any?): Boolean {
        return try {
            driver.executeScript(
                """
                if (!window.jibriRecorderApi) return false;
                window.jibriRecorderApi.executeCommand('$command', ...arguments);
                return true;
                """,
                *args
            ) as? Boolean ?: false
        } catch (t: Throwable) {
            logger.error("Error executing recorder command $command", t)
            false
        }
    }

    override fun getNumParticipants(): Int {
        val result = callRecorderApiAsync(
            "getRoomsInfo",
            """
            api.getRoomsInfo().then(roomsData => {
                const mainRoom = (roomsData.rooms || []).find(r => r?.isMainRoom);
                cb(mainRoom?.participants?.length || 1);
            }).catch(() => cb(1));
            """
        ) as? Number
        val count = result?.toInt() ?: 1
        logger.debug("Number of participants: $count")
        return count
    }

    override fun isCallEmpty() = getNumParticipants() <= 1

    @Suppress("UNCHECKED_CAST")
    override fun getBitrates(): Map<String, Any?> {
        val result = callRecorderApiAsync(
            "getConnectionStats",
            """
            api.getConnectionStats().then(stats => cb(stats || {})).catch(() => cb({}));
            """
        )
        val stats = result as? Map<String, Any?> ?: mapOf()
        return stats["bitrate"] as? Map<String, Any?> ?: mapOf()
    }

    override fun injectParticipantTrackerScript(): Boolean = true

    override fun injectLocalParticipantTrackerScript(): Boolean = true

    override fun getParticipants(): List<Map<String, Any>> {
        val result = callRecorderApiAsync(
            "getRoomsInfo",
            """
            api.getRoomsInfo().then(roomsData => {
                const mainRoom = (roomsData.rooms || []).find(r => r?.isMainRoom);
                const participants = (mainRoom?.participants || [])
                    .filter(p => p?.userContext?.id || p?.userContext?.name || p?.userContext?.email)
                    .map(p => ({ user: p.userContext }));
                cb(participants);
            }).catch(() => cb([]));
            """
        )
        @Suppress("UNCHECKED_CAST")
        return result as? List<Map<String, Any>> ?: listOf()
    }

    override fun numRemoteParticipantsJigasi(): Int {
        val result = callRecorderApiAsync(
            "getRoomsInfo",
            """
            api.getRoomsInfo().then(roomsData => {
                const mainRoom = (roomsData.rooms || []).find(r => r?.isMainRoom);
                const numJigasiParticipants = (mainRoom?.participants || []).filter(p => p?.isJigasi === true).length;
                cb(numJigasiParticipants);
            }).catch(() => cb(0));
            """
        ) as? Number
        val numJigasiParticipants = result?.toInt() ?: 0
        logger.debug("Number of Jigasi participants: $numJigasiParticipants")
        return numJigasiParticipants
    }

    override fun numHiddenParticipants(): Int {
        val result = callRecorderApiAsync(
            "getRoomsInfo",
            """
            api.getRoomsInfo(true).then(roomsData => {
                const mainRoom = (roomsData.rooms || []).find(r => r?.isMainRoom);
                const numHidden = (mainRoom?.participants || []).filter(p => p?.isHidden === true).length;
                cb(numHidden);
            }).catch(() => cb(0));
            """
        ) as? Number
        val numHidden = result?.toInt() ?: 0
        logger.debug("Number of hidden participants: $numHidden")
        return numHidden
    }

    override fun isIceConnected(): Boolean {
        val result = callRecorderApiAsync(
            "getConnectionStats",
            """
            api.getConnectionStats()
                .then(stats => cb(stats?.iceConnected === true))
                .catch(() => cb(false));
            """
        )
        val iceConnected = result as? Boolean ?: false
        logger.debug("ICE connected: $iceConnected")
        return iceConnected
    }

    override fun isLocalParticipantKicked(): Boolean {
        return try {
            driver.executeScript(
                "return window.jibriPageState.localParticipantKicked === true;"
            ) as? Boolean ?: false
        } catch (t: Throwable) {
            logger.error("Error checking isLocalParticipantKicked", t)
            false
        }
    }

    /**
     * Returns a count of how many remote participants are totally muted (audio
     * and video). We ignore jigasi participants as they may be muted in their presence
     * but also hard muted via the device, and we later ignore their state.
     * Note: Excludes hidden participants.
     */
    override fun numRemoteParticipantsMuted(): Int {
        val result = callRecorderApiAsync(
            "getRoomsInfo",
            """
            api.getRoomsInfo().then(roomsData => {
                const mainRoom = (roomsData.rooms || []).find(r => r?.isMainRoom);
                const numMutedParticipants = (mainRoom?.participants || []).filter(p => {
                    return p?.audioMuted === true && p?.videoMuted === true && p?.isJigasi !== true;
                }).length;
                cb(numMutedParticipants);
            }).catch(() => cb(0));
            """
        ) as? Number
        val numMutedParticipants = result?.toInt() ?: 0
        logger.debug("Number of muted participants: $numMutedParticipants")
        return numMutedParticipants
    }

    override fun isVisitor(): Boolean {
        return try {
            driver.executeScript(
                "return window.jibriRecorderApi && window.jibriRecorderApi.isVisitor() === true;"
            ) as? Boolean ?: false
        } catch (t: Throwable) {
            logger.error("Error calling jibriRecorderApi.isVisitor", t)
            false
        }
    }

    override fun isLocalAudioMuted(): Boolean {
        val result = callRecorderApiAsync(
            "isAudioMuted",
            """
            api.isAudioMuted().then(muted => cb(muted === true)).catch(() => cb(true));
            """
        ) as? Boolean
        return result ?: true
    }

    override fun isLocalVideoMuted(): Boolean {
        val result = callRecorderApiAsync(
            "isVideoMuted",
            """
            api.isVideoMuted().then(muted => cb(muted === true)).catch(() => cb(true));
            """
        ) as? Boolean
        return result ?: true
    }

    override fun isAudioForceMuted(): Boolean = isForceMuted("audio")

    override fun isVideoForceMuted(): Boolean = isForceMuted("video")

    /** Returns true if AV moderation is enabled for [mediaType] and the local participant is not approved to unmute. */
    private fun isForceMuted(mediaType: String): Boolean {
        val result = callRecorderApiAsync(
            "isParticipantForceMuted",
            """
            const localId = window.jibriPageState.localParticipantId;
            if (!localId) {
                cb(false);
                return;
            }
            api.isParticipantForceMuted(localId, '$mediaType').then(forceMuted => cb(forceMuted === true)).catch(() => cb(false));
            """
        )
        return result as? Boolean ?: false
    }

    /**
     * Toggles audio/video mute, then polls [isMutedMethod] until it
     * reflects the change. Unmuting (re-acquiring mic/camera) is slower than muting, so it
     * gets more time. Returns the last known state on timeout.
     *
     * If [requiredInitialState] is set, the current state is checked and the toggle only
     * executes if it matches.
     */
    private fun toggleMute(
        mediaType: String,
        isMutedMethod: String,
        toggleCommand: String,
        requiredInitialState: Boolean? = null,
    ): Any? {
        val guard = if (requiredInitialState != null) {
            "if (initialMuted !== $requiredInitialState) { cb('ok skipped alreadyMuted=' + initialMuted); return; }"
        } else {
            ""
        }
        val result = callRecorderApiAsync(
            isMutedMethod,
            """
            (async () => {
                try {
                    const initialMuted = await api.$isMutedMethod();
                    $guard
                    api.executeCommand('$toggleCommand');
                    const deadline = Date.now() + (initialMuted ? 12000 : 5000);
                    while (Date.now() < deadline) {
                        const nowMuted = await api.$isMutedMethod();
                        if (nowMuted !== initialMuted) {
                            return cb('ok wasMuted=' + initialMuted + ' nowMuted=' + nowMuted);
                        }
                        await new Promise(r => setTimeout(r, 200));
                    }
                    cb('timeout wasMuted=' + initialMuted);
                } catch (e) {
                    cb('error: ' + e);
                }
            })();
            """
        )
        if (result is String && result.startsWith("ok")) {
            logger.debug("Toggle $mediaType mute result: $result")
        } else {
            logger.warn("Toggle $mediaType mute did not complete cleanly: $result")
        }
        return result
    }

    override fun toggleVideoMute(): Any? = toggleMute("video", "isVideoMuted", "toggleVideo")

    override fun toggleAudioMute(): Any? = toggleMute("audio", "isAudioMuted", "toggleAudio")

    override fun raiseHand(): Boolean = executeRecorderCommand("toggleRaiseHand")

    override fun addToPresence(key: String, value: String): Boolean = setParticipantProperties(mapOf(key to value))

    override fun sendPresence(): Boolean = true

    override fun leave(): Boolean {
        if (!executeRecorderCommand("hangup")) return false

        return try {
            // videoConferenceLeft flips conferenceJoined back to false once the XMPP leave completes.
            WebDriverWait(driver, Duration.ofSeconds(5)).until {
                driver.executeScript("return window.jibriPageState?.conferenceJoined === false;") as? Boolean ?: false
            }
            true
        } catch (e: TimeoutException) {
            logger.error("Timed out waiting for videoConferenceLeft after hangup")
            false
        }
    }

    override fun setParticipantProperties(properties: Map<String, String>): Boolean {
        return try {
            val result = executeRecorderCommand("setParticipantProperties", properties, true)
            if (!result) {
                logger.warn("Could not set participant properties, External API not ready")
            }
            result
        } catch (t: Throwable) {
            logger.error("Error setting participant properties", t)
            false
        }
    }
}
