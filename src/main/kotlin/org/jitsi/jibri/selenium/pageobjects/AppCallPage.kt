package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration
import kotlin.time.measureTimedValue

/**
 * Page object representing the in-call page on a jitsi-meet server.
 * NOTE that all the calls here which execute javascript may throw (if, for example, chrome has crashed).  It is
 * intentional that this exceptions are propagated up: the caller should handle those cases.
 */
class AppCallPage(driver: RemoteWebDriver) : AbstractPageObject(driver), CallPage {
    private val logger = createLogger()

    init {
        PageFactory.initElements(driver, this)
    }

    override fun visit(url: CallUrlInfo): Boolean {
        if (!super.visit(url)) {
            return false
        }
        val (result, totalTime) = measureTimedValue {
            try {
                WebDriverWait(driver, Duration.ofSeconds(30)).until {
                    val result = driver.executeScript(
                        """
                        try {
                            return APP.conference._room.isJoined();
                        } catch (e) {
                            return e.message;
                        }
                        """.trimMargin()
                    )
                    when (result) {
                        is Boolean -> result
                        else -> {
                            logger.debug { "Not joined yet: $result" }
                            false
                        }
                    }
                }
                true
            } catch (t: TimeoutException) {
                logger.error("Timed out waiting for call page to load")
                false
            }
        }

        if (result) {
            logger.info("Waited $totalTime to join the conference")
        }

        return result
    }

    /**
     * Enable the local camera and microphone. Used in sip gateway mode.
     */
    override fun unmute() = doUnmute("Audio") && doUnmute("Video")

    private fun doUnmute(mediaType: String): Boolean {
        return try {
            val result = driver.executeScript(
                """
                    try {
                            APP.conference.mute$mediaType(false);
                        } catch (e) {
                            return e.message;
                        }
                """.trimMargin()
            )
            if (result != null) {
                logger.info { "Failed to unmute $mediaType: $result" }
                return false
            }
            return true
        } catch (t: TimeoutException) {
            logger.error("Timed out waiting for unmute $mediaType")
            false
        }
    }

    /** Returns the number of participants excluding hidden participants. */
    override fun getNumParticipants(): Int {
        val result = driver.executeScript(
            """
            try {
                return (APP.conference._room.getParticipants().$PARTICIPANT_FILTER_SCRIPT).length + 1;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Number -> result.toInt()
            else -> 1
        }
    }

    /**
     * Return true if there are no other participants in the conference.
     */
    override fun isCallEmpty() = getNumParticipants() <= 1

    @Suppress("UNCHECKED_CAST")
    private fun getStats(): Map<String, Any?> {
        val result = driver.executeScript(
            """
            try {
                return APP.conference.getStats();
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        if (result is String) {
            return mapOf()
        }
        return result as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getBitrates(): Map<String, Any?> {
        val stats = getStats()
        return stats.getOrDefault("bitrate", mapOf<String, Any?>()) as Map<String, Any?>
    }

    override fun injectParticipantTrackerScript(): Boolean {
        val result = driver.executeScript(
            """
            try {
                window._jibriParticipants = new Map();
                const existingMembers = APP.conference._room.room.members || {};
                const existingMemberJids = Object.keys(existingMembers);
                console.log("There were " + existingMemberJids.length + " existing members");
                existingMemberJids.forEach(jid => {
                    const existingMember = existingMembers[jid];
                    const nick = existingMember.nick;
                    if (existingMember.identity) {
                        console.log("Member ", existingMember, " has identity, adding");
                        if (nick && nick.length > 0 && existingMember.identity.user) {
                            existingMember.identity.user.name = nick;
                        }
                        window._jibriParticipants.set(jid, existingMember.identity);
                    } else {
                        console.log("Member ", existingMember.jid, " has no identity, skipping");
                    }
                });
                APP.conference._room.room.addListener(
                    "xmpp.muc_member_joined",
                    (from, nick, role, hidden, statsid, status, identity) => {
                        console.log("Jibri got MUC_MEMBER_JOINED: ", from, identity);
                        if (!hidden && identity) {
                            if (nick && nick.length > 0 && identity.user) {
                                identity.user.name = nick;
                            }
                            window._jibriParticipants.set(from, identity);
                        }
                    }
                );
                APP.conference._room.room.addListener(
                    "xmpp.display_name_changed",
                    (jid, displayName) => {
                        const identity = window._jibriParticipants.get(jid);
                        if (displayName && displayName.length > 0 && identity && identity.user) {
                            identity.user.name = displayName;
                        }
                    }
                );

                return true;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Boolean -> result
            else -> false
        }
    }

    override fun injectLocalParticipantTrackerScript(): Boolean {
        val result = driver.executeScript(
            """
            try {
                window._isLocalParticipantKicked=false

                APP.conference._room.room.addListener(
                    "xmpp.kicked",
                    (isSelfPresence, actorId, kickedParticipantId, reason) => {
                        console.log("Jibri got a KICKED event: ", isSelfPresence, actorId, kickedParticipantId, reason);
                        if (isSelfPresence) {
                            window._isLocalParticipantKicked=true
                        }
                    }
                );

                return true;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Boolean -> result
            else -> false
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getParticipants(): List<Map<String, Any>> {
        val result = driver.executeScript(
            """
            try {
                return window._jibriParticipants.values().toArray();
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        if (result is List<*>) {
            return result as List<Map<String, Any>>
        } else {
            return listOf()
        }
    }

    /**
     * Return how many of the participants are Jigasi clients.
     * Note: excludes any participants that are hidden (for example transcribers)
     */
    override fun numRemoteParticipantsJigasi(): Int {
        val result = driver.executeScript(
            """
            try {
                return APP.conference._room.getParticipants()
                    .$PARTICIPANT_FILTER_SCRIPT
                    .filter(participant => participant.getProperty("features_jigasi") == true)
                    .length;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Number -> result.toInt()
            else -> {
                logger.error("error running numRemoteParticipantsJigasi script: $result ${result?.javaClass}")
                0
            }
        }
    }

    /** How many of the participants are hidden or hiddenFromRecorder. */
    override fun numHiddenParticipants(): Int {
        val result = driver.executeScript(
            """
            try {
                return APP.conference._room.getParticipants()
                    .filter(p => (p.isHidden() || p.isHiddenFromRecorder()))
                    .length;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Number -> result.toInt()
            else -> {
                logger.error("error running numHiddenParticipants script: $result ${result?.javaClass}")
                0
            }
        }
    }

    /**
     * Return true if ICE is connected.
     */
    override fun isIceConnected(): Boolean {
        val result: Any? = driver.executeScript(
            """
            try {
                return APP.conference.getConnectionState();
            } catch(e) {
                return e.message;
            }
        """
        )
        return (result.toString().lowercase() == "connected").also {
            if (!it) {
                logger.warn("ICE not connected: $result")
            }
        }
    }

    override fun isLocalParticipantKicked(): Boolean {
        val result = driver.executeScript(
            """
            try {
                return window._isLocalParticipantKicked;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        if (result is Boolean) {
            return result
        } else {
            return false
        }
    }

    /**
     * Returns a count of how many remote participants are totally muted (audio
     * and video). We ignore jigasi participants as they maybe muted in their presence
     * but also hard muted via the device, and we later ignore their state.
     * Note: Excludes hidden participants.
     */
    override fun numRemoteParticipantsMuted(): Int {
        val result = driver.executeScript(
            """
            try {
                return APP.conference._room.getParticipants()
                    .$PARTICIPANT_FILTER_SCRIPT
                    .filter(participant => participant.isAudioMuted() && participant.isVideoMuted()
                                && participant.getProperty("features_jigasi") !== true)
                    .length;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Number -> result.toInt()
            else -> {
                logger.error("error running numRemoteParticipantsMuted script: $result ${result?.javaClass}")
                0
            }
        }
    }

    override fun isVisitor(): Boolean {
        val result = driver.executeScript(
            """
            try {
                return APP.store.getState()['features/visitors']?.iAmVisitor === true;
            } catch (e) {
                return false;
            }
            """.trimMargin()
        )
        return result as? Boolean ?: false
    }

    override fun isLocalAudioMuted(): Boolean {
        val result = driver.executeScript(
            """
            try {
                return APP.conference.isLocalAudioMuted();
            } catch (e) {
                return true;
            }
            """.trimMargin()
        )
        return result as? Boolean ?: true
    }

    override fun isLocalVideoMuted(): Boolean {
        val result = driver.executeScript(
            """
            try {
                return APP.conference.isLocalVideoMuted();
            } catch (e) {
                return true;
            }
            """.trimMargin()
        )
        return result as? Boolean ?: true
    }

    /** Returns true if AV moderation is enabled for audio and the local participant is not approved to unmute. */
    override fun isAudioForceMuted(): Boolean {
        val result = driver.executeScript(
            """
            try {
                var state = APP.store.getState();
                var avMod = state['features/av-moderation'];
                if (!avMod || !avMod.audioModerationEnabled) return false;
                var local = state['features/base/participants']?.local;
                if (local && local.role === 'moderator') return false;
                return avMod.audioUnmuteApproved !== true;
            } catch (e) {
                return false;
            }
            """.trimMargin()
        )
        return result as? Boolean ?: false
    }

    /** Returns true if AV moderation is enabled for video and the local participant is not approved to unmute. */
    override fun isVideoForceMuted(): Boolean {
        val result = driver.executeScript(
            """
            try {
                var state = APP.store.getState();
                var avMod = state['features/av-moderation'];
                if (!avMod || !avMod.videoModerationEnabled) return false;
                var local = state['features/base/participants']?.local;
                if (local && local.role === 'moderator') return false;
                return avMod.videoUnmuteApproved !== true;
            } catch (e) {
                return false;
            }
            """.trimMargin()
        )
        return result as? Boolean ?: false
    }

    // Toggles video mute and waits for the Redux store to confirm the state change.
    // muteVideo() returns a Promise — firing it synchronously and returning immediately
    // would not guarantee the toggle completed. Unmuting requires re-acquiring the camera
    // device, which is slower (12s timeout) than muting/releasing it (5s). On timeout,
    // extra state is returned to help diagnose why the change did not complete.
    override fun toggleVideoMute(): Any? {
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20))
        return driver.executeAsyncScript(
            """
            var done = arguments[0];
            try {
                var isMuted = APP.conference.isLocalVideoMuted();
                var unsubscribe;
                var timer;
                var cleanup = function() { clearTimeout(timer); if (unsubscribe) unsubscribe(); };

                var waitForChange = function(ms, timeoutMsg) {
                    timer = setTimeout(function() {
                        cleanup();
                        var st = APP.store.getState();
                        var vt = st['features/base/tracks'].filter(function(t) { return t.local && t.mediaType === 'video'; });
                        done(timeoutMsg + ' mediaMuted=' + st['features/base/media'].video.muted +
                             ' videoTracks=' + vt.length + ' hasJitsiTrack=' + vt.some(function(t) { return !!t.jitsiTrack; }));
                    }, ms);
                    unsubscribe = APP.store.subscribe(function() {
                        var nowMuted = APP.conference.isLocalVideoMuted();
                        if (nowMuted !== isMuted) {
                            cleanup();
                            done('ok wasMuted=' + isMuted + ' nowMuted=' + nowMuted);
                        }
                    });
                };

                if (!isMuted) {
                    // Prefer muting via the track directly so the Promise rejection is catchable.
                    // Fall back to a Redux dispatch when no track is present yet.
                    var localVideo = APP.store.getState()['features/base/tracks']
                        .find(function(t) { return t.local && t.mediaType === 'video' && t.jitsiTrack; });
                    if (localVideo) {
                        waitForChange(5000, 'timeout-muting');
                        localVideo.jitsiTrack.mute().catch(function(e) { cleanup(); done('error-mute: ' + e); });
                    } else {
                        waitForChange(5000, 'timeout-mute-no-track');
                        APP.store.dispatch({ type: 'SET_VIDEO_MUTED', muted: true });
                    }
                } else {
                    // ensureTrack tells Jitsi to re-acquire the camera if no track exists yet.
                    waitForChange(12000, 'timeout-unmuting');
                    APP.store.dispatch({ type: 'SET_VIDEO_MUTED', muted: false, ensureTrack: true });
                }
            } catch (e) {
                done('error: ' + e.message);
            }
            """.trimMargin()
        )
    }

    // Toggles audio mute and waits for the Redux store to confirm the state change.
    // Audio track operations (especially unmuting/re-acquiring the microphone) are async —
    // dispatching and returning immediately would not guarantee the toggle completed.
    // Unmuting has a longer timeout (12s) than muting (5s) because re-acquiring a device
    // is slower than releasing it. On timeout, extra state is returned to help diagnose
    // why the change did not complete.
    override fun toggleAudioMute(): Any? {
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20))
        return driver.executeAsyncScript(
            """
            var done = arguments[0];
            try {
                var isMuted = APP.conference.isLocalAudioMuted();
                var unsubscribe;
                var timer;
                var cleanup = function() { clearTimeout(timer); if (unsubscribe) unsubscribe(); };

                var waitForChange = function(ms, timeoutMsg) {
                    timer = setTimeout(function() {
                        cleanup();
                        var st = APP.store.getState();
                        var at = st['features/base/tracks'].filter(function(t) { return t.local && t.mediaType === 'audio'; });
                        done(timeoutMsg + ' mediaMuted=' + st['features/base/media'].audio.muted +
                             ' audioTracks=' + at.length + ' hasJitsiTrack=' + at.some(function(t) { return !!t.jitsiTrack; }));
                    }, ms);
                    unsubscribe = APP.store.subscribe(function() {
                        var nowMuted = APP.conference.isLocalAudioMuted();
                        if (nowMuted !== isMuted) {
                            cleanup();
                            done('ok wasMuted=' + isMuted + ' nowMuted=' + nowMuted);
                        }
                    });
                };

                if (!isMuted) {
                    // Prefer muting via the track directly so the Promise rejection is catchable.
                    // Fall back to a Redux dispatch when no track is present yet.
                    var localAudio = APP.store.getState()['features/base/tracks']
                        .find(function(t) { return t.local && t.mediaType === 'audio' && t.jitsiTrack; });
                    if (localAudio) {
                        waitForChange(5000, 'timeout-muting');
                        localAudio.jitsiTrack.mute().catch(function(e) { cleanup(); done('error-mute: ' + e); });
                    } else {
                        waitForChange(5000, 'timeout-mute-no-track');
                        APP.store.dispatch({ type: 'SET_AUDIO_MUTED', muted: true });
                    }
                } else {
                    // ensureTrack tells Jitsi to re-acquire the microphone if no track exists yet.
                    waitForChange(12000, 'timeout-unmuting');
                    APP.store.dispatch({ type: 'SET_AUDIO_MUTED', muted: false, ensureTrack: true });
                }
            } catch (e) {
                done('error: ' + e.message);
            }
            """.trimMargin()
        )
    }

    override fun raiseHand(): Boolean {
        val result = driver.executeScript(
            """
            try {
                const local = APP.store.getState()['features/base/participants']?.local;
                const isRaised = local && local.raisedHandTimestamp > 0;
                APP.store.dispatch({
                    type: 'LOCAL_PARTICIPANT_RAISE_HAND',
                    raisedHandTimestamp: isRaised ? 0 : Date.now()
                });
                return true;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return result is Boolean && result
    }

    /**
     * Add the given key, value pair to the presence map and send a new presence
     * message
     */
    override fun addToPresence(key: String, value: String): Boolean {
        val result = driver.executeScript(
            """
            try {
                APP.conference._room.room.addOrReplaceInPresence(
                    arguments[0],
                    {
                        value: arguments[1]
                    }
                );
            } catch (e) {
                return e.message;
            }
            """.trimMargin(),
            key,
            value
        )
        return when (result) {
            is String -> false
            else -> true
        }
    }

    override fun sendPresence(): Boolean {
        val result = driver.executeScript(
            """
            try {
                APP.conference._room.room.sendPresence();
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is String -> false
            else -> true
        }
    }

    override fun leave(): Boolean {
        val result = driver.executeScript(
            """
            try {
                return APP.conference._room.leave();
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )

        // Let's wait till we are alone in the room
        // (give time for the js Promise to finish before quiting selenium)
        WebDriverWait(driver, Duration.ofSeconds(2)).until {
            getNumParticipants() == 1
        }

        return when (result) {
            is String -> false
            else -> true
        }
    }

    companion object {
        /**
         * Javascript to apply a filter to the list of participants to exclude ones which should be hidden from jibri.
         */
        const val PARTICIPANT_FILTER_SCRIPT = "filter(p => !(p.isHidden() || p.isHiddenFromRecorder()))"
    }
}
