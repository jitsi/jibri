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

package org.jitsi.jibri.capture.ffmpeg.executor

import org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.LoggingUtils
import org.jitsi.jibri.util.ProcessFactory
import org.jitsi.jibri.util.ProcessFailedToStart
import org.jitsi.jibri.util.ProcessState
import org.jitsi.jibri.util.ProcessStatePublisher
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.StatusPublisher
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.getLoggerWithHandler
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
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
 * [FfmpegExecutor] is responsible for executing ffmpeg.  This interface
 * allows different executors to be implemted so that settings may be varied
 * per platform.
 */
class FfmpegExecutor(
    private val processFactory: ProcessFactory = ProcessFactory(),
    private val processStatePublisherProvider: (ProcessWrapper) -> ProcessStatePublisher = ::ProcessStatePublisher
) : StatusPublisher<ProcessState>() {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private var processLoggerTask: Future<Boolean>? = null
    /**
     * The currently active (if any) Ffmpeg process
     */
    private var currentFfmpegProc: ProcessWrapper? = null
    private var processStatePublisher: ProcessStatePublisher? = null

    companion object {
        private val ffmpegOutputLogger = getLoggerWithHandler("ffmpeg", FfmpegFileHandler())
    }
    /**
     * Launch ffmpeg with the given [FfmpegExecutorParams] and using
     * the given [Sink]
     */
    fun launchFfmpeg(command: List<String>) {
        currentFfmpegProc = processFactory.createProcess(command)
        logger.info("Starting ffmpeg with command ${command.joinToString(separator = " ")} ($command)")
        try {
            currentFfmpegProc?.let {
                it.start()
                processStatePublisher = processStatePublisherProvider(it)
                processStatePublisher!!.addStatusHandler(this::publishStatus)
                processLoggerTask = LoggingUtils.logOutput(it, ffmpegOutputLogger)
            }
        } catch (t: Throwable) {
            logger.error("Error starting ffmpeg", t)
            currentFfmpegProc = null
            publishStatus(ProcessState(ProcessFailedToStart(), ""))
        }
    }

    /**
     * Shutdown ffmpeg gracefully (if possible)
     */
    fun stopFfmpeg() {
        logger.info("Stopping ffmpeg process")
        processStatePublisher?.stop()
        currentFfmpegProc?.apply {
            stop()
            waitFor(10, TimeUnit.SECONDS)
            if (isAlive) {
                logger.error("Ffmpeg didn't stop, killing ffmpeg")
                destroyForcibly()
            }
        }
        processLoggerTask?.get()
        logger.info("Ffmpeg exited with value ${currentFfmpegProc?.exitValue}")
    }
}
