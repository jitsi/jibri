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

package org.jitsi.jibri

import org.jitsi.jibri.api.xmpp.JibriMode
import java.nio.file.Path

/**
 * Top-level parent for any exception thrown inside the Jibri code base.  All other exceptions
 * should be caught and turned into some kind of [JibriException].
 */
sealed class JibriException(msg: String) : Exception(msg)

abstract class JobCompleted(msg: String) : JibriException(msg)
object EmptyCallException : JobCompleted("Call is empty")
object RemoteSideHungUp : JobCompleted("Remote side hung up")

sealed class JibriError(msg: String) : JibriException(msg)
fun JibriError.shouldRetry(): Boolean {
    return this !is JibriRequestError
}
/**
 * [JibriRequestError]s represent errors due to some value in the initial request being invalid and therefore the
 * request should not be retried.
 */
sealed class JibriRequestError(msg: String) : JibriError(msg)
class CallUrlInfoFromJidException(message: String) : JibriRequestError(message)
class UnsupportedIqMode(mode: JibriMode) : JibriRequestError("Unsupported IQ mode $mode")
class BadRtmpUrl(msg: String) : JibriRequestError(msg)
class RtmpUrlNotAllowed(url: String) : JibriRequestError("RTMP URL $url is not allowed")

/**
 * [JibriSessionError]s represent transient errors that occur while a session was running.  The request can/should be
 * retried.
 */
sealed class JibriSessionError(msg: String) : JibriError(msg)
object JibriBusy : JibriSessionError("Jibri is busy")
class BrokenPipe(msg: String) : JibriSessionError(msg)
object NoMediaReceivedException : JibriSessionError("No media received")
class ProcessHung(msg: String) : JibriSessionError(msg)
object ChromeHung : JibriSessionError("Chrome hung")
object FailedToJoinCall : JibriSessionError("Failed to join the call")
// I think this should be an error since there would still be participants in the call and we'd want to notify them
// that this is what happened
object ClientMuteLimitExceeded : JibriSessionError(
    "No media received due to all clients muting for longer than the timeout"
)
// TODO: unclear how this should be treated (error? job completed? might need to
// create different exceptions for different cases if need arises)
// I'm _pretty_ sure in our current use cases that it should be an error, because
// while we're monitoring processes we never expect them to exit
class ProcessExited(msg: String) : JibriSessionError(msg)
object RemoteSipClientBusy : JibriSessionError("Remote side busy")

/**
 * [JibriSystemError]s represent errors that are fundamental to Jibri and will
 * put the Jibri in an overall [JibriState.Error] state.
 */
sealed class JibriSystemError(msg: String) : JibriError(msg)
class ErrorCreatingRecordingsDirectory(error: Throwable, path: Path) :
    JibriSystemError("Could not create recordings directory $path: ${error.message}")

class RecordingsDirectoryNotWritable(path: String) :
    JibriSystemError("Unable to write to recordings directory $path")

class UnsupportedOsException(msg: String) : JibriSystemError(msg)

class ProcessFailedToStart(msg: String) : JibriSystemError(msg)
