package org.jitsi.jibri.util

class ProcessMonitor(
        private val processToMonitor: MonitorableProcess,
        // If the process never even started, we won't be able to get an
        // exit code, so this callback needs to support being passed null
        private val processIsDeadCallback: (exitCode: Int?) -> Unit) : Runnable {

    override fun run() {
        if (!processToMonitor.isAlive()) {
            processIsDeadCallback(processToMonitor.getExitCode())
        }
    }
}