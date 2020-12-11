/*
 * Copyright @ 2018 - present 8x8, Inc.
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

package org.jitsi.jibri.pjsua

import org.jitsi.jibri.UnsupportedOsException
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.util.OsType
import org.jitsi.jibri.util.getOsType
import java.util.logging.FileHandler

private const val PJSUA_SCRIPT_FILE_LOCATION = "/opt/jitsi/jibri/pjsua.sh"
const val PJSUA_X_DISPLAY = ":1"

fun getPjsuaCommandLinux(sipClientParams: SipClientParams): List<String> {
    return buildList {
        add(PJSUA_SCRIPT_FILE_LOCATION)
        if (sipClientParams.isAuthenticated()) {
            add("--id=${sipClientParams.displayName} <sip:${sipClientParams.userName}>")
            add("--registrar=sip:${sipClientParams.userName!!.substringAfter('@')}")
            add("--realm=*")
            add("--username=${sipClientParams.userName.substringBefore('@')}")
            add("--password=${sipClientParams.password}")
        } else {
            add("--id=${sipClientParams.displayName} <sip:jibri@127.0.0.1>")
        }

        if (sipClientParams.autoAnswer) {
            add("--auto-answer-timer=30")
            add("--auto-answer=200")
        } else {
            add("sip:${sipClientParams.sipAddress}")
        }
    }
}

private fun SipClientParams.isAuthenticated(): Boolean {
    return userName != null && password != null
}

fun getPjsuaCommand(sipClientParams: SipClientParams, osDetector: () -> OsType = ::getOsType): List<String> {
    return when (val os = osDetector()) {
        is OsType.Linux -> getPjsuaCommandLinux(sipClientParams)
        else -> throw UnsupportedOsException("Pjsua not supported on ${os.osStr}")
    }
}

/**
 * A distinct [FileHandler] so that we can configure the file
 * Ffmpeg logs to separately in the logging config
 */
object PjsuaFileHandler : FileHandler()
