package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.util.FfmpegFileHandler
import org.jitsi.jibri.capture.ffmpeg.util.OutputParser
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.Tail
import org.jitsi.jibri.util.Tee
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.pid
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

val FFMPEG_RESTART_ATTEMPTS = 1
/**
 * [AbstractFfmpegExecutor] contains logic common to launching Ffmpeg across platforms.
 * It is abstract, and requires a subclass to implement the
 * [getFfmpegCommand] method to return the proper command.
 */
abstract class AbstractFfmpegExecutor : FfmpegExecutor {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val ffmpegOutputLogger = Logger.getLogger("ffmpeg")
    private val executor = Executors.newFixedThreadPool(3, NameableThreadFactory("AbstractFfmpegExecutor"))
    /**
     * The currently active (if any) Ffmpeg process
     */
    var currentFfmpegProc: Process? = null
    /**
     * In order to both analyze Ffmpeg's output live and log it to a file, we'll
     * tee its stdout stream so that both consumers can get all of its output.
     */
    var ffmpegOutputTee: Tee? = null
    /**
     * We'll use this to monitor the stdout output of the Ffmpeg process
     */
    var ffmpegTail: Tail? = null

    init {
        ffmpegOutputLogger.useParentHandlers = false
        ffmpegOutputLogger.addHandler(FfmpegFileHandler())
    }

    /**
     * Get the shell command to use to launch ffmpeg
     */
    protected abstract fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink) {
        val command = getFfmpegCommand(ffmpegExecutorParams, sink)

        val pb = ProcessBuilder(command.split(" "))
        pb.redirectErrorStream(true)
        logger.info("Running ffmpeg command:\n $command")
        currentFfmpegProc = pb.start()
        // Tee ffmpeg's output so that we can analyze its status and log everything
        ffmpegOutputTee = Tee(currentFfmpegProc!!.inputStream)
        // Keep reading from the initial inputstream
        executor.submit {
            while (true) {
                ffmpegOutputTee?.read()
            }
        }
        ffmpegTail = Tail(ffmpegOutputTee!!.addBranch())
        // Read from a tee branch and log to a file
        executor.submit {
            val reader = BufferedReader(InputStreamReader(ffmpegOutputTee!!.addBranch()))
            while (true) {
                ffmpegOutputLogger.info(reader.readLine())
            }
        }

        logger.debug("Launched ffmpeg, is it alive? ${currentFfmpegProc?.isAlive}")
    }

    override fun getExitCode(): Int? {
        currentFfmpegProc?.let {
            if (it.isAlive) return null
            return it.exitValue()
        }
        return null
    }

    override fun isHealthy(): Boolean {
        //TODO: should we only consider 2 sequential instances of not getting a frame=
        // encoding line "unhealthy"?
        currentFfmpegProc?.let {
            if (!it.isAlive) {
                return false
            }
            val ffmpegOutput = ffmpegTail?.mostRecentLine ?: ""
            val result = OutputParser().parse(ffmpegOutput)
            if (!result.containsKey("frame")) {
                logger.error("Ffmpeg is running but doesn't appear to be encoding.  Its most recent line was $ffmpegOutput")
                return false
            } else {
                logger.debug("Ffmpeg appears healthy: $result")
                return true
            }
        }
        return false
    }

    override fun stopFfmpeg() {
        executor.shutdown()
        currentFfmpegProc?.let {
            val pid = pid(it)
            logger.info("Sending SIGINT to ffmpeg proc $pid")
            Runtime.getRuntime().exec("kill -s SIGINT $pid")
        } ?: run {
            logger.info("Ffmpeg had already exited")
        }
        currentFfmpegProc?.waitFor(10, TimeUnit.SECONDS)
        currentFfmpegProc?.isAlive.let {
            // This isn't great, as killing ffmpeg this way will corrupt
            // the entire recording (from what I've seen)
            currentFfmpegProc?.destroyForcibly()
        }
        logger.info("FfmpegCapturer exited with ${currentFfmpegProc?.exitValue()}")
    }
}
