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

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.util.extensions.error
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.WebDriverWait
import java.util.logging.Logger

/**
 * Page object representing the in-call page on a jitsi-meet server
 */
class CallPage(driver: RemoteWebDriver) : AbstractPageObject(driver) {
    private val logger = Logger.getLogger(this::class.qualifiedName)

    init {
        PageFactory.initElements(driver, this)
    }

    override fun visit(callUrlInfo: CallUrlInfo): Boolean {
        if (!super.visit(callUrlInfo)) {
            return false
        }
        val start = System.currentTimeMillis()
        return try {
            WebDriverWait(driver, 30).until {
                val result = driver.executeScript("""
                    try {
                        return typeof(APP.conference._room) !== 'undefined';
                    } catch (e) {
                        return e.message;
                    }
                    """.trimMargin()
                )
                when (result) {
                    is Boolean -> result
                    else -> false
                }
            }
            val totalTime = System.currentTimeMillis() - start
            logger.info("Waited $totalTime milliseconds for call page to load")
            true
        } catch (t: TimeoutException) {
            logger.error("Timed out waiting for call page to load")
            false
        }
    }

    fun getNumParticipants(driver: RemoteWebDriver): Long {
        val result = driver.executeScript("""
            try {
                return APP.conference.membersCount;
            } catch (e) {
                return e.message;
            }
        """.trimMargin())
        return when (result) {
            is Long -> result
            else -> 1
        }
    }

    fun injectParticipantTrackerScript(driver: RemoteWebDriver): Boolean {
        val result = driver.executeScript("""
            try {
                window._jibriParticipants = [];
                APP.conference._room.room.addListener(
                    "xmpp.muc_member_joined",
                    (from, nick, role, hidden, statsid, status, identity) => {
                        window._jibriParticipants.push(identity);
                    }
                );
                return true;
            } catch (e) {
                return e.message;
            }
        """.trimMargin())
        return when (result) {
            is Boolean -> result
            else -> false
        }
    }

    fun getParticipants(driver: RemoteWebDriver): List<Map<String, Any>> {
        val result = driver.executeScript("""
            try {
                return window._jibriParticipants;
            } catch (e) {
                return e.message;
            }
        """.trimMargin())
        if (result is List<*>) {
            @Suppress("UNCHECKED_CAST")
            return result as List<Map<String, Any>>
        } else {
            return listOf()
        }
    }

    fun leave(): Boolean {
        val result = driver.executeScript("""
            try {
                return APP.conference._room.connection.disconnect();
            } catch (e) {
                return e.message;
            }
        """.trimMargin())
        return when (result) {
            is String -> false
            else -> true
        }
    }
}
