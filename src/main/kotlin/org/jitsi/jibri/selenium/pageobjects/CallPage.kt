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

package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.WebDriverWait
import kotlin.time.measureTimedValue

/**
 * Page object representing the in-call page on a jitsi-meet server.
 * NOTE that all the calls here which execute javascript may throw (if, for example, chrome has crashed).  It is
 * intentional that this exceptions are propagated up: the caller should handle those cases.
 */
class CallPage(driver: RemoteWebDriver) : AbstractPageObject(driver) {
    private val logger = createLogger()

    init {
        PageFactory.initElements(driver, this)
    }

    override fun visit(url: String): Boolean {
        if (!super.visit(url)) {
            return false
        }
        val (result, totalTime) = measureTimedValue {
            try {
                WebDriverWait(driver, 30).until {
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

    fun getNumParticipants(): Int {
        val result = driver.executeScript(
            """
            try {
                return APP.conference.membersCount;
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
    fun getBitrates(): Map<String, Any?> {
        val stats = getStats()
        return stats.getOrDefault("bitrate", mapOf<String, Any?>()) as Map<String, Any?>
    }

    fun injectParticipantTrackerScript(): Boolean {
        val result = driver.executeScript(
            """
            try {
                window._jibriParticipants = [];
                const existingMembers = APP.conference._room.room.members || {};
                const existingMemberJids = Object.keys(existingMembers);
                console.log("There were " + existingMemberJids.length + " existing members");
                existingMemberJids.forEach(jid => {
                    const existingMember = existingMembers[jid];
                    if (existingMember.identity) {
                        console.log("Member ", existingMember, " has identity, adding");
                        window._jibriParticipants.push(existingMember.identity);
                    } else {
                        console.log("Member ", existingMember.jid, " has no identity, skipping");
                    }
                });
                APP.conference._room.room.addListener(
                    "xmpp.muc_member_joined",
                    (from, nick, role, hidden, statsid, status, identity) => {
                        console.log("Jibri got MUC_MEMBER_JOINED: ", from, identity);
                        if (identity) {
                            window._jibriParticipants.push(identity);
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

    fun getParticipants(): List<Map<String, Any>> {
        val result = driver.executeScript(
            """
            try {
                return window._jibriParticipants;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        if (result is List<*>) {
            @Suppress("UNCHECKED_CAST")
            return result as List<Map<String, Any>>
        } else {
            return listOf()
        }
    }

    /**
     * Return how many of the participants are Jigasi clients
     */
    fun numRemoteParticipantsJigasi(): Int {
        val result = driver.executeScript(
            """
            try {
                return APP.conference._room.getParticipants()
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
                logger.error("error running numRemoteParticipantsJigasi script: $result ${result::class.java}")
                0
            }
        }
    }

    /**
     * Returns a count of how many remote participants are totally muted (audio
     * and video).
     */
    fun numRemoteParticipantsMuted(): Int {
        val result = driver.executeScript(
            """
            try {
                return APP.conference._room.getParticipants()
                    .filter(participant => participant.isAudioMuted() && participant.isVideoMuted())
                    .length;
            } catch (e) {
                return e.message;
            }
            """.trimMargin()
        )
        return when (result) {
            is Number -> result.toInt()
            else -> {
                logger.error("error running numRemoteParticipantsMuted script: $result ${result::class.java}")
                0
            }
        }
    }

    /**
     * Add the given key, value pair to the presence map and send a new presence
     * message
     */
    fun addToPresence(key: String, value: String): Boolean {
        val result = driver.executeScript(
            """
            try {
                APP.conference._room.room.addToPresence(
                    '$key',
                    {
                        value: '$value'
                    }
                );
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

    fun sendPresence(): Boolean {
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

    fun leave(): Boolean {
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
        WebDriverWait(driver, 2).until {
            getNumParticipants() == 1
        }

        return when (result) {
            is String -> false
            else -> true
        }
    }
}
