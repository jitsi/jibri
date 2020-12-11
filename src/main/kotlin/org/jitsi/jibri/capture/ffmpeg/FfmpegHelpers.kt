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

package org.jitsi.jibri.capture.ffmpeg

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jitsi.jibri.ProcessExited
import org.jitsi.jibri.ProcessHung
import org.jitsi.jibri.util.EOF
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.ifNoDataFor
import org.jitsi.utils.secs

class FfmpegHelpers {
    companion object {
        // We monitor for errors and process exit in the same place, that way we know we process all log messages
        // for errors (to get a more specific failure exception) rather than quitting as soon as we see the process
        // exit.
        suspend fun watchForProcessError(process: ProcessWrapper) {
            // Inject code into the flow which will throw an exception if, after the first message, we don't see
            // any output after 5 seconds.
            val flow = process.output.ifNoDataFor(5.secs) { throw ProcessHung("${process.name} hung") }
            flow.takeWhile { it != EOF }.collect {
                OutputParser.checkForErrors(it)
            }
            throw ProcessExited("Process ${process.name} exited with code ${process.exitValue}")
        }

        suspend fun onEncodingStart(process: ProcessWrapper, onStartedEncoding: () -> Unit) {
            process.output.takeWhile { !OutputParser.isEncoding(it) }.collect { }
            onStartedEncoding()
        }
    }
}
