package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.Duration
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
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
        val callParams: CallParams,
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
class FileRecordingJibriService(val recordingOptions: RecordingOptions) : JibriService() {
    /**
     * The [Logger] for this class
     */
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * The [JibriSelenium] this class will use for joining a web call
     */
    private val jibriSelenium = JibriSelenium(
        JibriSeleniumOptions(callParams = recordingOptions.callParams),
        Executors.newSingleThreadScheduledExecutor()
    )
    /**
     * The [FfmpegCapturer] that will be used to capture media from the call and write it to a file
     */
    private val capturer = FfmpegCapturer()
    /**
     * The [Sink] this class will use to model the file on the filesystem
     */
    private var sink: Sink
    /**
     * If ffmpeg dies for some reason, we want to restart it.  This [ScheduledExecutorService]
     * will run the process monitor in a separate thread so it can check that it's running on its own
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
                callName = recordingOptions.callParams.callUrlInfo.callName
        )
        jibriSelenium.addStatusHandler {
            publishStatus(it)
        }
    }

    /**
     * @see [JibriService.start]
     */
    override fun start() {
        jibriSelenium.joinCall(recordingOptions.callParams.callUrlInfo.callName)
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, sink)
        var numRestarts = 0
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            if (exitCode != null) {
                logger.error("Capturer process is no longer healthy.  It exited with code $exitCode")
            } else {
                logger.error("Capturer process is no longer healthy, but it is still running, stopping it now")
                capturer.stop()
            }
            if (numRestarts == 1) {
                logger.error("Giving up on restarting the capturer")
                publishStatus(JibriServiceStatus.ERROR)
                processMonitorTask?.cancel(false)
            } else {
                numRestarts++
                // Re-create the sink here because we want a new filename
                sink = createSink(
                    recordingsDirectory = recordingOptions.recordingDirectory,
                    callName = recordingOptions.callParams.callUrlInfo.callName
                )
                capturer.start(capturerParams, sink)
            }
        }
        processMonitorTask = executor.scheduleAtFixedRate(
            period = 10,
            unit = TimeUnit.SECONDS,
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
