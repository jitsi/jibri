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

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.util.ENCODING_KEY
import org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler
import org.jitsi.jibri.capture.ffmpeg.util.OutputParser
import org.jitsi.jibri.capture.ffmpeg.util.WARNING_KEY
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.Tail
import org.jitsi.jibri.util.Tee
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.stopProcess
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Executors
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
    private var currentFfmpegProc: Process? = null
    /**
     * In order to both analyze Ffmpeg's output live and log it to a file, we'll
     * tee its stdout stream so that both consumers can get all of its output.
     */
    private var ffmpegOutputTee: Tee? = null
    /**
     * We'll use this to monitor the stdout output of the Ffmpeg process
     */
    private var ffmpegTail: Tail? = null

    init {
        ffmpegOutputLogger.useParentHandlers = false
        ffmpegOutputLogger.addHandler(FfmpegFileHandler())
    }

    /**
     * Get the shell command to use to launch ffmpeg
     */
    protected abstract fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): Boolean {
        processBuilder.command(getFfmpegCommand(ffmpegExecutorParams, sink).split(" "))
        processBuilder.redirectErrorStream(true)
        logger.info("Running ffmpeg command:\n ${processBuilder.command()}")
        try {
            currentFfmpegProc = processBuilder.start()
        } catch (e: IOException) {
            logger.error("Error starting ffmpeg: $e")
            return false
        }
        // Tee ffmpeg's output so that we can analyze its status and log everything
        ffmpegOutputTee = Tee(currentFfmpegProc!!.inputStream)
        ffmpegTail = Tail(ffmpegOutputTee!!.addBranch())
        // Read from a tee branch and log to a file
        executor.submit {
            val reader = BufferedReader(InputStreamReader(ffmpegOutputTee!!.addBranch()))
            while (true) {
                ffmpegOutputLogger.info(reader.readLine())
            }
        }

        logger.debug("Launched ffmpeg, is it alive? ${currentFfmpegProc?.isAlive}")
        return true
    }

    override fun getExitCode(): Int? {
        if (currentFfmpegProc?.isAlive == true) {
            return null
        }
        return currentFfmpegProc?.exitValue()
    }

    override fun isHealthy(): Boolean {
        //TODO: should we only consider 2 sequential instances of not getting a frame=
        // encoding line "unhealthy"?
        currentFfmpegProc?.let {
            val ffmpegOutput = ffmpegTail?.mostRecentLine ?: ""
            val parsedOutputLine = OutputParser().parse(ffmpegOutput)
            if (!it.isAlive) {
                logger.error("Ffmpeg is no longer running, its most recent output line was: $ffmpegOutput")
                return false
            }

            return if (parsedOutputLine.containsKey(ENCODING_KEY) || parsedOutputLine.containsKey(WARNING_KEY)) {
                if (parsedOutputLine.containsKey(WARNING_KEY)) {
                    logger.debug("Ffmpeg is encoding, but issued a warning: $parsedOutputLine")
                } else {
                    logger.debug("Ffmpeg appears healthy: $parsedOutputLine")
                }
                true
            } else {
                logger.error("Ffmpeg is running but doesn't appear to be encoding.  " +
                        "Its most recent output line was $ffmpegOutput")
                false
            }
        }
        return false
    }

    override fun stopFfmpeg() {
        executor.shutdown()
        stopProcess(currentFfmpegProc, "ffmpeg", logger)
    }
}
