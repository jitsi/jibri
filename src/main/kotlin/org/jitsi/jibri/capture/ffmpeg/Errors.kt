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

import org.jitsi.jibri.error.JibriError
import org.jitsi.jibri.status.ErrorScope

open class FfmpegError(scope: ErrorScope, detail: String) : JibriError(scope, detail)
object FfmpegFailedToStart : FfmpegError(ErrorScope.SYSTEM, "Ffmpeg failed to start")
class FfmpegUnexpectedSignal(outputLine: String) : FfmpegError(ErrorScope.SESSION, outputLine)
class BadRtmpUrl(outputLine: String) : FfmpegError(ErrorScope.SESSION, outputLine) {
    override fun shouldRetry(): Boolean = false
}
class BrokenPipe(outputLine: String) : FfmpegError(ErrorScope.SESSION, outputLine)
class QuitUnexpectedly(outputLine: String) : FfmpegError(ErrorScope.SESSION, outputLine)
