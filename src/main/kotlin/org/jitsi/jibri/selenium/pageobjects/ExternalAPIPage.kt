package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.PageFactory

/** CallPage implementation with External API event-driven updates. */
class ExternalAPIPage(driver: RemoteWebDriver) : AbstractPageObject(driver), CallPage {
    private val logger = createLogger()

    init {
        PageFactory.initElements(driver, this)
    }

    override fun visit(url: CallUrlInfo): Boolean {
        val room = extractRoomName(url).lowercase()
        val recorderUrl = "https://192.168.1.4:8443/recorder.html?room=$room"
        logger.info("Loading recorder page for room=$room")
        driver.get(recorderUrl)
        return true
    }

    private fun extractRoomName(url: CallUrlInfo): String {
        return url.callName
    }

    override fun unmute() = true

    override fun getNumParticipants(): Int {
        return try {
            val result = driver.executeScript(
                "return window.jibriPageState ? window.jibriPageState.getRemoteParticipantCount() + 1 : 1;"
            )
            val count = (result as? Number)?.toInt() ?: 1
            logger.info("getNumParticipants: result=$result, count=$count")
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
