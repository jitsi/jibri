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
package org.jitsi.jibri.sipgateway.pjsua

import org.jitsi.jibri.util.ProcessWrapper

enum class PjsuaStatus {
    HEALTHY,
    EXITED
}

class PjsuaProcessWrapper(
    command: List<String>,
    environment: Map<String, String>,
    processBuilder: ProcessBuilder = ProcessBuilder()
) : ProcessWrapper<PjsuaStatus>(command, environment, processBuilder) {

    override fun getStatus(): Pair<PjsuaStatus, String> {
        val mostRecentLine = getMostRecentLine()
        val status = if(isAlive) PjsuaStatus.HEALTHY else PjsuaStatus.EXITED
        return Pair(status, mostRecentLine)
    }
}
