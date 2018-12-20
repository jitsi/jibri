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

package org.jitsi.jibri.capture.ffmpeg

import org.jitsi.jibri.capture.Capturer2
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.executor.OutputParser2
import org.jitsi.jibri.capture.ffmpeg.executor.getFfmpegCommandLinux
import org.jitsi.jibri.capture.ffmpeg.executor.getFfmpegCommandMac
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.OsDetector
import org.jitsi.jibri.util.OsType
import org.jitsi.jibri.util.ProcessState
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.debug
import java.util.logging.Logger

/**
 * [FfmpegCapturer2] is responsible for launching ffmpeg, capturing from the
 * configured audio and video devices, and writing to the given [Sink]
 */
class FfmpegCapturer2(
    osDetector: OsDetector = OsDetector(),
    private val ffmpegExecutor: FfmpegExecutor = FfmpegExecutor()
) : Capturer2, StatusPublisher<ComponentState>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val getCommand: (Sink) -> List<String>
    private val ffmpegStatusStateMachine = FfmpegStatusStateMachine()

    companion object {
        const val COMPONENT_ID = "Ffmpeg Capturer"
    }

    init {
        val osType = osDetector.getOsType()
        logger.debug("Detected os as OS: $osType")
        getCommand = when (osType) {
            OsType.MAC -> { sink: Sink -> getFfmpegCommandMac(FfmpegExecutorParams(), sink) }
            OsType.LINUX -> { sink: Sink -> getFfmpegCommandLinux(FfmpegExecutorParams(), sink) }
            else -> throw UnsupportedOsException()
        }
        ffmpegExecutor.addStatusHandler(this::onFfmpegStateUpdate)
        ffmpegStatusStateMachine.onStateTransition(this::onFfmpegStateChange)
    }

    /**
     * Start the capturer and write to the given [Sink].  Returns
     * true on success, false otherwise
     */
    override fun start(sink: Sink) {
        val command = getCommand(sink)
        ffmpegExecutor.launchFfmpeg(command)
    }

    private fun onFfmpegStateUpdate(ffmpegState: ProcessState) {
        val status = OutputParser2.parse(ffmpegState.mostRecentOutput)
        ffmpegStatusStateMachine.transition(status.toFfmpegEvent())
    }

    private fun onFfmpegStateChange(oldState: ComponentState, newState: ComponentState) {
        logger.info("Ffmpeg transition from state $oldState to $newState")
        publishStatus(newState)
    }

    /**
     * Stops the capturer
     */
    override fun stop() = ffmpegExecutor.stopFfmpeg()
}
