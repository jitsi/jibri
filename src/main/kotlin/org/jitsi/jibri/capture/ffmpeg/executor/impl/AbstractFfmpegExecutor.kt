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

package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.FfmpegProcessWrapper
import org.jitsi.jibri.capture.ffmpeg.FfmpegStatus
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.logStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

const val FFMPEG_RESTART_ATTEMPTS = 1
/**
 * [AbstractFfmpegExecutor] contains logic common to launching Ffmpeg across platforms.
 * It is abstract, and requires a subclass to implement the
 * [getFfmpegCommand] method to return the proper command.
 */
abstract class AbstractFfmpegExecutor(private val processBuilder: ProcessBuilder = ProcessBuilder()) : FfmpegExecutor {
    private val logger = Logger.getLogger(AbstractFfmpegExecutor::class.qualifiedName)
    private val ffmpegOutputLogger = Logger.getLogger("ffmpeg")
    private val executor = Executors.newSingleThreadExecutor(NameableThreadFactory("AbstractFfmpegExecutor"))
    /**
     * The currently active (if any) Ffmpeg process
     */
    private var currentFfmpegProc: FfmpegProcessWrapper? = null

    init {
        ffmpegOutputLogger.useParentHandlers = false
        ffmpegOutputLogger.addHandler(FfmpegFileHandler())
    }

    /**
     * Get the shell command to use to launch ffmpeg
     */
    protected abstract fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): Boolean {
        val command = getFfmpegCommand(ffmpegExecutorParams, sink).split(" ")
        try {
            currentFfmpegProc = FfmpegProcessWrapper(command, processBuilder = processBuilder)
        } catch (t: Throwable) {
            logger.error("Error starting ffmpeg: $t")
            return false
        }
        return currentFfmpegProc?.let {
            logger.info("Starting ffmpeg with command $command")
            it.start()
            logStream(it.getOutput(), ffmpegOutputLogger, executor)
            true
        } ?: run {
            false
        }
    }

    override fun getExitCode(): Int? {
        if (currentFfmpegProc?.isAlive == true) {
            return null
        }
        return currentFfmpegProc?.exitValue()
    }

    override fun isHealthy(): Boolean {
        return currentFfmpegProc?.let {
            val (status, mostRecentOutput) = it.getStatus()
            return@let when (status) {
                FfmpegStatus.HEALTHY -> {
                    logger.debug("Ffmpeg appears healthy: $mostRecentOutput")
                    true
                }
                FfmpegStatus.WARNING -> {
                    logger.info("Ffmpeg is encoding, but issued a warning: $mostRecentOutput")
                    true
                }
                FfmpegStatus.ERROR -> {
                    logger.error("Ffmpeg is running but doesn't appear to be encoding: $mostRecentOutput")
                    false
                }
                FfmpegStatus.EXITED -> {
                    logger.error("Ffmpeg exited with code ${getExitCode()}.  It's most recent output was $mostRecentOutput")
                    false
                }
            }
        } ?: run {
            false
        }
    }

    override fun stopFfmpeg() {
        executor.shutdown()
        logger.info("Stopping ffmpeg process")
        currentFfmpegProc?.apply {
            stop()
            waitFor(10, TimeUnit.SECONDS)
            if (isAlive) {
                logger.error("Ffmpeg didn't stop, killing ffmpeg")
                destroyForcibly()
            }
        }
        logger.info("Ffmpeg exited with value ${currentFfmpegProc?.exitValue()}")
    }
}
