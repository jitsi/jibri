package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.Duration
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.error
import org.jitsi.jibri.util.scheduleAtFixedRate
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

data class RecordingOptions(
        /**
         * The directory in which recordings should be created
         */
        val recordingDirectory: File,
        /**
         * The url and call name for the web call to record from
         */
        val callUrlInfo: CallUrlInfo,
        /**
         * The filesystem path to the script which should be executed when
         *  the recording is finished.
         */
        val finalizeScriptPath: String
)

/**
 * [FileRecordingJibriService] is the [JibriService] responsible for joining
 * a web call, capturing its audio and video, and writing that audio and video
 * to a file to be replayed later.
 */
class FileRecordingJibriService(val recordingOptions: RecordingOptions) : JibriService {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = recordingOptions.callUrlInfo.baseUrl))
    private val capturer = FfmpegCapturer()
    private var sink: Sink
    /**
     * The [ScheduledExecutorService] we'll use to run the process monitor
     */
    private val executor = Executors.newSingleThreadScheduledExecutor()
    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null
    init {
        sink = createSink(
                recordingsDirectory = recordingOptions.recordingDirectory,
                callName = recordingOptions.callUrlInfo.callName
        )
    }

    /**
     * @see [JibriService.start]
     */
    override fun start() {
        jibriSelenium.joinCall(recordingOptions.callUrlInfo.callName)
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, sink)
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            logger.error("Capturer process is no longer running, exited " +
                    "with code $exitCode.  Restarting")
            // Re-create the sink here because we want a new filename
            sink = createSink(
                    recordingsDirectory = recordingOptions.recordingDirectory,
                    callName = recordingOptions.callUrlInfo.callName
            )
            capturer.start(capturerParams, sink)
        }
        processMonitorTask = executor.scheduleAtFixedRate(
                period = Duration( 10, TimeUnit.SECONDS),
                action = processMonitor)
    }

    /**
     * @see [JibriService.stop]
     */
    override fun stop() {
        processMonitorTask?.cancel(true)
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
        logger.info("Finalizing the recording")
        finalize()
    }

    /**
     * Helper to execute the finalize script and wait for its completion.
     * NOTE that this will block for however long the finalize script takes
     * to complete (by design)
     */
    private fun finalize() {
        try {
            val finalizeProc = Runtime.getRuntime().exec(recordingOptions.finalizeScriptPath)
            finalizeProc.waitFor()
            logger.info("Recording finalize script finished with exit " +
                    "value: ${finalizeProc.exitValue()}")
        } catch (e: IOException) {
            logger.error("Failed to run finalize script: $e")
        }
    }

    /**
     * Helper to create the [FileSink] we'll write the media to
     */
    private fun createSink(recordingsDirectory: File, callName: String): Sink {
        return FileSink(
                recordingsDirectory = recordingsDirectory,
                callName = callName
        )
    }
}