package org.jitsi.jibri.util

/**
 * [ProcessMonitor] takes in a [MonitorableProcess] and a callback.  When
 * its [run] method is executed, it will check the status of the monitored
 * process and, if it is no longer alive, will invoke the given callback
 */
class ProcessMonitor(
        /**
         * The process to monitor
         */
        private val processToMonitor: MonitorableProcess,
        /**
         * The callback to invoke if [processToMonitor] is found to be dead.
         * If the process never even started, we won't be able to get an
         * exit code, so this callback needs to support being passed null
         */
        private val processIsDeadCallback: (exitCode: Int?) -> Unit) : Runnable {

    /**
     * Run a check of [processToMonitor] to check if it is alive.  If it is
     * dead, [processIsDeadCallback] will be invoked with the exit code from
     * [processToMonitor] (or null, if there isn't one)
     */
    override fun run() {
        if (!processToMonitor.isAlive()) {
            processIsDeadCallback(processToMonitor.getExitCode())
        }
    }
}