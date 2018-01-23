package org.jitsi.jibri.util

import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

/**
 * Monitor a process to verify that it is still alive, if it is found to be
 * dead, invoke a callback
 */
class ProcessMonitor(
        private val processToMonitor: MonitorableProcess,
        // If the process never even started, we won't be able to get an
        // exit code, so this callback needs to support being passed null
        private val processIsDeadCallback: (exitCode: Int?) -> Unit)
{
    private val timer: Timer = Timer()
    private val aliveCheckIntervalMillis: Long = 60 * 1000

    @Volatile
    private var running: Boolean = false

    @Synchronized
    fun startMonitoring() {
        if (running) {
            return
        }
        running = true
        timer.scheduleAtFixedRate(0, aliveCheckIntervalMillis) {
            checkAlive()
        }
    }

    @Synchronized
    private fun checkAlive()
    {
        if (running)
        {
            if (!processToMonitor.isAlive())
            {
                running = false
                timer.cancel()
                processIsDeadCallback(processToMonitor.getExitCode())
            }
        }
    }

    @Synchronized
    fun stopMonitoring()
    {
        running = false
        timer.cancel()
    }
}