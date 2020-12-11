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

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jitsi.jibri.ProcessExited
import org.jitsi.jibri.ProcessHung
import org.jitsi.jibri.RemoteSideHungUp
import org.jitsi.jibri.RemoteSipClientBusy
import org.jitsi.jibri.util.EOF
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.ifNoDataFor
import org.jitsi.utils.secs

class PjsuaHelpers {
    companion object {
        suspend fun watchForProcessError(pjsuaProcess: ProcessWrapper) {
            val flow = pjsuaProcess.output.ifNoDataFor(5.secs) { throw ProcessHung("${pjsuaProcess.name} hung") }
            // We don't currently parse any of Pjsua's output, just take action based on its exit value
            flow.takeWhile { it != EOF }.collect { }
            when (pjsuaProcess.exitValue) {
                0 -> throw RemoteSideHungUp
                2 -> throw RemoteSipClientBusy
                else -> throw ProcessExited("Pjsua exited with code ${pjsuaProcess.exitValue}")
            }
        }
    }
}
