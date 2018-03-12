package org.jitsi.jibri.service.impl

import org.jitsi.jibri.CallParams
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.ffmpeg.executor.impl.FFMPEG_RESTART_ATTEMPTS
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.extensions.error
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 *
 */
abstract class AbstractFfmpegSeleniumService(
        private val callParams: CallParams) : JibriService() {
    /**
     * The [Logger] for this class
     */
    protected abstract val logger: Logger

    /**
     * Used for the selenium interaction
     */
    private val jibriSelenium = JibriSelenium(
        JibriSeleniumOptions(callParams),
        Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("JibriSelenium"))
    )

    /**
     * The [FfmpegCapturer] that will be used to capture media from the call and write it to a file
     */
    protected val capturer: Capturer = FfmpegCapturer()

    /**
     * If ffmpeg dies for some reason, we want to restart it.  This [ScheduledExecutorService]
     * will run the process monitor in a separate thread so it can check that it's running on its own
     */
    private val executor =
        Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("AbstractFfmpegSeleniumService"))
    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null

    abstract fun getSink(): Sink

    override fun start(): Boolean {
        if (!jibriSelenium.joinCall(callParams.callUrlInfo.callName)) {
            logger.error("Selenium failed to join the call")
            stop()
            return false
        }
        capturer.start(getSink())
        processMonitorTask = addProcessMonitor()
        return true
    }

    private fun addProcessMonitor(): ScheduledFuture<*>? {
        var numRestarts = 0
        val processMonitor = ProcessMonitor(capturer) { exitCode ->
            if (exitCode != null) {
                logger.error("Capturer process is no longer healthy.  It exited with code $exitCode")
            } else {
                logger.error("Capturer process is no longer healthy but it is still running, stopping it now")
                capturer.stop()
            }
            if (numRestarts == FFMPEG_RESTART_ATTEMPTS) {
                logger.error("Giving up on restarting the capturer")
                publishStatus(JibriServiceStatus.ERROR)
                stop()
            } else {
                numRestarts++
                capturer.start(getSink())
            }
        }
        return executor.scheduleAtFixedRate(
            processMonitor,
            30,
            10,
            TimeUnit.SECONDS
        )
    }

    override fun stop() {
        processMonitorTask?.cancel(true)
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
    }
}
