package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/** CallPage implementation with External API event-driven updates. */
class ExternalAPIPage(driver: RemoteWebDriver) : AbstractPageObject(driver), CallPage {
    private val logger = createLogger()

    /**
     * The bundled recorder.html, extracted to a local file so we can load it
     * directly in the browser instead of relying on the deployment to serve it.
     * Extracted lazily and reused for the lifetime of the page object.
     */
    private val recorderHtmlFile: File by lazy { extractRecorderHtml() }

    init {
        PageFactory.initElements(driver, this)
    }

    override fun visit(url: CallUrlInfo): Boolean {
        val room = url.callName.substringAfterLast('/').lowercase()
        // The deployment base url is passed to the page so it can source
        // external_api.js from the deployment (the page itself is loaded from a
        // local file and has no deployment origin of its own).
        val baseUrl = url.baseUrl
        // Convert tenant dots to slashes for URL path format.
        val tenantPath = if (url.tenant.isNotEmpty()) url.tenant.replace(".", "/") else ""
        val recorderUrl = buildString {
            append(recorderHtmlFile.toURI().toString())
            append("?room=").append(encode(room))
            append("&baseUrl=").append(encode(baseUrl))
            if (tenantPath.isNotEmpty()) {
                append("&tenant=").append(encode(tenantPath))
            }
        }
        logger.info("Loading recorder page for room=$room baseUrl=$baseUrl tenant=$tenantPath from $recorderUrl")
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

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    override fun unmute() = true

    override fun getNumParticipants(): Int {
        return try {
            val result = driver.executeScript(
                "return window.jibriPageState ? window.jibriPageState.getRemoteParticipantCount() + 1 : 1;"
            )
            val count = (result as? Number)?.toInt() ?: 1
            logger.debug("getNumParticipants: result=$result, count=$count")
            count
        } catch (t: Throwable) {
            logger.error("Error getting participant count: ${t.message}")
            1
        }
    }

    override fun isCallEmpty() = getNumParticipants() <= 1

    @Suppress("UNCHECKED_CAST")
    override fun getBitrates(): Map<String, Any?> = mapOf()

    override fun injectParticipantTrackerScript(): Boolean = true

    override fun injectLocalParticipantTrackerScript(): Boolean = true

    override fun getParticipants(): List<Map<String, Any>> = listOf()

    override fun numRemoteParticipantsJigasi(): Int = 0

    override fun numHiddenParticipants(): Int = 0

    override fun isIceConnected(): Boolean = true

    override fun isLocalParticipantKicked(): Boolean = false

    override fun numRemoteParticipantsMuted(): Int = 0

    override fun isVisitor(): Boolean = false

    override fun isLocalAudioMuted(): Boolean = true

    override fun isLocalVideoMuted(): Boolean = true

    override fun isAudioForceMuted(): Boolean = false

    override fun isVideoForceMuted(): Boolean = false

    override fun toggleVideoMute(): Any? = null

    override fun toggleAudioMute(): Any? = null

    override fun raiseHand(): Boolean = true

    override fun addToPresence(key: String, value: String): Boolean = true

    override fun sendPresence(): Boolean = true

    override fun leave(): Boolean = true
}
