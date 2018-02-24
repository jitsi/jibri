package org.jitsi.jibri.capture.ffmpeg.executor.impl

import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.util.OutputParser
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.Tail
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.pid
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.StringBufferInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * [AbstractFfmpegExecutor] contains logic common to launching Ffmpeg across platforms.
 * It is abstract, and requires a subclass to implement the
 * [getFfmpegCommand] method to return the proper command.
 */
abstract class AbstractFfmpegExecutor : FfmpegExecutor {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * The currently active (if any) Ffmpeg process
     */
    var currentFfmpegProc: Process? = null
    /**
     * We'll use this to monitor the stdout output of the Ffmpeg process
     */
    var ffmpegTail: Tail? = null

    /**
     * Get the shell command to use to launch ffmpeg
     */
    protected abstract fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String

    override fun launchFfmpeg(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink) {
        val command = getFfmpegCommand(ffmpegExecutorParams, sink)

        val pb = ProcessBuilder(command.split(" "))
        pb.redirectErrorStream(true)
        //TODO: if we redirect here, we can't monitor stdout to check on the status of ffmpeg.
        // if we want to log ffmpeg output (which i think we should), we'll need to "tee" the stdout
        // stream to go to both a file and to a stream that can be used for monitoring its output directly
        // via Tail
        //pb.redirectOutput(File("/tmp/ffmpeg.out"))

        logger.info("Running ffmpeg command:\n $command")
        currentFfmpegProc = pb.start()
        ffmpegTail = Tail(currentFfmpegProc!!.inputStream)
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
            val output = OutputParser().parse(ffmpegOutput)
            if (output.isEmpty()) {
                logger.error("Ffmpeg is running but doesn't appear to be encoding.  Its most recent line was $ffmpegOutput")
                return false
            } else {
                logger.debug("Ffmpeg appears healthy: $output")
                return true
            }
        }
        return false
    }

    override fun stopFfmpeg() {
        ffmpegTail?.stop()
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
