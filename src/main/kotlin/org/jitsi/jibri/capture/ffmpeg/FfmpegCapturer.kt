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

import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.OsDetector
import org.jitsi.jibri.util.OsType
import org.jitsi.jibri.util.ProcessExited
import org.jitsi.jibri.util.ProcessFailedToStart
import org.jitsi.jibri.util.ProcessState
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.getLoggerWithHandler
import java.util.logging.Logger

/**
 * Parameters which will be passed to ffmpeg
 */
data class FfmpegExecutorParams(
    val resolution: String = "1280x720",
    val framerate: Int = 30,
    val videoEncodePreset: String = "veryfast",
    val queueSize: Int = 4096,
    val streamingMaxBitrate: Int = 2976,
    val streamingBufSize: Int = streamingMaxBitrate * 2,
        // The range of the CRF scale is 0–51, where 0 is lossless,
        // 23 is the default, and 51 is worst quality possible. A lower value
        // generally leads to higher quality, and a subjectively sane range is
        // 17–28. Consider 17 or 18 to be visually lossless or nearly so;
        // it should look the same or nearly the same as the input but it
        // isn't technically lossless.
        // https://trac.ffmpeg.org/wiki/Encode/H.264#crf
    val h264ConstantRateFactor: Int = 25,
    val gopSize: Int = framerate * 2
)

/**
 * [FfmpegCapturer] is responsible for launching ffmpeg, capturing from the
 * configured audio and video devices, and writing to the given [Sink]
 */
class FfmpegCapturer(
    osDetector: OsDetector = OsDetector(),
    private val ffmpeg: JibriSubprocess = JibriSubprocess("ffmpeg", ffmpegOutputLogger)
) : Capturer, StatusPublisher<ComponentState>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val getCommand: (Sink) -> List<String>
    private val ffmpegStatusStateMachine = FfmpegStatusStateMachine()

    companion object {
        const val COMPONENT_ID = "Ffmpeg Capturer"
        private val ffmpegOutputLogger = getLoggerWithHandler("ffmpeg", FfmpegFileHandler())
    }

    init {
        val osType = osDetector.getOsType()
        logger.debug("Detected os as OS: $osType")
        getCommand = when (osType) {
            OsType.MAC -> { sink: Sink -> getFfmpegCommandMac(FfmpegExecutorParams(), sink) }
            OsType.LINUX -> { sink: Sink -> getFfmpegCommandLinux(FfmpegExecutorParams(), sink) }
            else -> throw UnsupportedOsException()
        }

        ffmpeg.addStatusHandler(this::onFfmpegProcessUpdate)
        ffmpegStatusStateMachine.onStateTransition(this::onFfmpegStateMachineStateChange)
    }

    /**
     * Start the capturer and write to the given [Sink].
     */
    override fun start(sink: Sink) {
        val command = getCommand(sink)
        ffmpeg.launch(command)
    }

    /**
     * Handle a [ProcessState] update from ffmpeg by parsing it into an [FfmpegEvent] and passing it to the state
     * machine
     */
    private fun onFfmpegProcessUpdate(ffmpegState: ProcessState) {
        // We handle the case where it failed to start separately, since there is no output
        if (ffmpegState.runningState is ProcessFailedToStart) {
            ffmpegStatusStateMachine.transition(FfmpegEvent.ErrorLine(ErrorScope.SYSTEM, "Ffmpeg failed to start"))
        } else if (ffmpegState.runningState is ProcessExited) {
            logger.info("Ffmpeg quit abruptly.  Last output line: ${ffmpegState.mostRecentOutput}")
            ffmpegStatusStateMachine.transition(FfmpegEvent.ErrorLine(ErrorScope.SESSION, "Ffmpeg failed to start"))
        } else {
            val status = OutputParser.parse(ffmpegState.mostRecentOutput)
            ffmpegStatusStateMachine.transition(status.toFfmpegEvent())
        }
    }

    private fun onFfmpegStateMachineStateChange(oldState: ComponentState, newState: ComponentState) {
        logger.info("Ffmpeg capturer transitioning from state $oldState to $newState")
        publishStatus(newState)
    }

    /**
     * Stops the capturer
     */
    override fun stop() = ffmpeg.stop()
}
