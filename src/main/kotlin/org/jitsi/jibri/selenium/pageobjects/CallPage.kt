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
import org.openqa.selenium.remote.RemoteWebDriver

interface CallPage {
    fun visit(url: CallUrlInfo): Boolean
    fun getNumParticipants(): Int
    fun isCallEmpty(): Boolean
    fun injectParticipantTrackerScript(): Boolean
    fun injectLocalParticipantTrackerScript(): Boolean
    fun getParticipants(): List<Map<String, Any>>
    fun unmute(): Boolean
    fun numRemoteParticipantsJigasi(): Int
    fun numHiddenParticipants(): Int
    fun isIceConnected(): Boolean
    fun isLocalParticipantKicked(): Boolean
    fun numRemoteParticipantsMuted(): Int
    fun isVisitor(): Boolean
    fun isLocalAudioMuted(): Boolean
    fun isLocalVideoMuted(): Boolean
    fun isAudioForceMuted(): Boolean
    fun isVideoForceMuted(): Boolean
    fun toggleVideoMute(): Any?
    fun toggleAudioMute(): Any?
    fun raiseHand(): Boolean
    fun addToPresence(key: String, value: String): Boolean
    fun sendPresence(): Boolean
    fun leave(): Boolean
    fun getBitrates(): Map<String, Any?>

    companion object {
        fun create(driver: RemoteWebDriver): CallPage {
            val useExternalAPI = System.getenv("JIBRI_USE_EXTERNAL_API") == "true"
            return if (useExternalAPI) {
                ExternalAPIPage(driver)
            } else {
                AppCallPage(driver)
            }
        }
    }
}
