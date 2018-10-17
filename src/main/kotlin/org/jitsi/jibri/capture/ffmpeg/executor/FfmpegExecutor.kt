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
import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.ProcessWrapper
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.getLoggerWithHandler
import org.jitsi.jibri.util.logStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

const val FFMPEG_RESTART_ATTEMPTS = 1

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
 * per platform
 */
class FfmpegExecutor(
    private val processBuilder: ProcessBuilder = ProcessBuilder()
) : MonitorableProcess {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val executor = Executors.newSingleThreadExecutor(NameableThreadFactory("FfmpegExecutor"))
    private var processLoggerTask: Future<Boolean>? = null
    /**
     * The currently active (if any) Ffmpeg process
     */
    private var currentFfmpegProc: ProcessWrapper? = null

    companion object {
        private val ffmpegOutputLogger = getLoggerWithHandler("ffmpeg", FfmpegFileHandler())
    }
    /**
     * Launch ffmpeg with the given [FfmpegExecutorParams] and using
     * the given [Sink]
     */
    fun launchFfmpeg(command: List<String>): Boolean {
        try {
            currentFfmpegProc = ProcessWrapper(command, processBuilder = processBuilder)
        } catch (t: Throwable) {
            logger.error("Error starting ffmpeg: $t")
            return false
        }
        return currentFfmpegProc?.let {
            logger.info("Starting ffmpeg with command $command")
            it.start()
            processLoggerTask = logStream(it.getOutput(), ffmpegOutputLogger, executor)
            true
        } ?: run {
            false
        }
    }

    /**
     * Shutdown ffmpeg gracefully (if possible)
     */
    fun stopFfmpeg() {
        logger.info("Stopping ffmpeg process")
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

    override fun getExitCode(): Int? = if (currentFfmpegProc?.isAlive == true) null else currentFfmpegProc?.exitValue

    override fun isHealthy(): Boolean = isFfmpegHealthy(currentFfmpegProc, logger)
}
