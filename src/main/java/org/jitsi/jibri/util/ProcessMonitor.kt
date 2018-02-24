package org.jitsi.jibri.util

import org.jitsi.jibri.util.extensions.error
import java.util.logging.Logger

/**
 * [ProcessMonitor] takes in a [MonitorableProcess] and a callback.  When
 * its [run] method is executed it will check the status of the monitored
 * process and, if it is no longer 'healthy' (the definition of 'healthy'
 * is left to the [MonitorableProcess]'s implementation), [ProcessMonitor]
 * will invoke the given [processUnhealthyCallback].
 */
class ProcessMonitor(
        private val processToMonitor: MonitorableProcess,
        private val processUnhealthyCallback: (exitCode: Int?) -> Unit
    ) : Runnable {
    private val logger = Logger.getLogger(this::class.qualifiedName)

    /**
     * Run a check of [processToMonitor] to check if it is alive.  If it is
     * dead, [processUnhealthyCallback] will be invoked with the exit code from
     * [processToMonitor] (or null, if there isn't one)
     */
    override fun run() {
        try {
            if (!processToMonitor.isHealthy()) {
                processUnhealthyCallback(processToMonitor.getExitCode())
            }
        } catch (t: Throwable) {
            logger.error("Error while determining process health: $t")
        }
    }
}