package org.jitsi.jibri.util

import java.util.*
import java.util.logging.Logger
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
    private val logger = Logger.getLogger(this::class.simpleName)
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
            logger.info("Process monitor checking if alive")
            if (!processToMonitor.isAlive())
            {
                logger.info("Process is dead")
                running = false
                //TODO: this prevents us from calling scheduleAtFixedRate again
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