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
import org.jitsi.jibri.capture.UnsupportedSinkTypeException
import org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.sink.impl.StreamSink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.JibriSubprocess
import org.jitsi.jibri.util.OsDetector
import org.jitsi.jibri.util.OsType
import org.jitsi.jibri.util.ProcessExited
import org.jitsi.jibri.util.ProcessFailedToStart
import org.jitsi.jibri.util.ProcessRunning
import org.jitsi.jibri.util.ProcessState
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.getLoggerWithHandler
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger

/**
 * [FfmpegCapturer] is responsible for launching ffmpeg, capturing from the
 * configured audio and video devices, and writing to the given [Sink]
 */
class FfmpegCapturer(
    parentLogger: Logger,
    osDetector: OsDetector = OsDetector(),
    ffmpeg: JibriSubprocess? = null
) : Capturer, StatusPublisher<ComponentState>() {
    private val logger = createChildLogger(parentLogger)
    private val ffmpeg = ffmpeg ?: JibriSubprocess(logger, "ffmpeg", ffmpegOutputLogger)
    private val getCommand: (Sink) -> List<String>
    private val ffmpegStatusStateMachine = FfmpegStatusStateMachine()

    companion object {
        const val COMPONENT_ID = "Ffmpeg Capturer"
        private val ffmpegOutputLogger = getLoggerWithHandler("ffmpeg", FfmpegFileHandler())

        val commandLinuxRecording: List<String> by config {
            "jibri.ffmpeg.command-linux-recording".from(Config.configSource)
        }
        val commandLinuxStreaming: List<String> by config {
            "jibri.ffmpeg.command-linux-streaming".from(Config.configSource)
        }
        val commandMacRecording: List<String> by config {
            "jibri.ffmpeg.command-mac-recording".from(Config.configSource)
        }
        val commandMacStreaming: List<String> by config {
            "jibri.ffmpeg.command-mac-streaming".from(Config.configSource)
        }
    }

    init {
        val osType = osDetector.getOsType()
        logger.debug { "Detected OS: $osType" }
        getCommand = when (osType) {
            OsType.MAC -> { sink: Sink ->
                when (sink) {
                    is StreamSink -> commandMacStreaming
                    is FileSink -> commandMacRecording
                    else -> throw UnsupportedSinkTypeException(sink)
                } + listOf(sink.path)
            }
            OsType.LINUX -> { sink: Sink ->
                when (sink) {
                    is StreamSink -> commandLinuxStreaming
                    is FileSink -> commandLinuxRecording
                    else -> throw UnsupportedSinkTypeException(sink)
                } + listOf(sink.path)
            }
            else -> throw UnsupportedOsException()
        }

        this.ffmpeg.addStatusHandler(this::onFfmpegProcessUpdate)
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
        when (ffmpegState.runningState) {
            is ProcessFailedToStart -> {
                ffmpegStatusStateMachine.transition(
                    FfmpegEvent.ErrorLine(FfmpegFailedToStart)
                )
            }
            else -> {
                if (ffmpegState.runningState is ProcessExited) {
                    logger.info("Ffmpeg quit abruptly.  Last output line: ${ffmpegState.mostRecentOutput}")
                }
                val status = OutputParser.parse(ffmpegState.mostRecentOutput)
                ffmpegStatusStateMachine.transition(status.toFfmpegEvent(ffmpegState.runningState is ProcessRunning))
            }
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
