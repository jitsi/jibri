package org.jitsi.jibri.util

import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * [ProcessStatePublisher] is responsible for publishing a [ProcessState] every time:
 * 1) The process writes a line of output (to stdout/stderr)
 * 2) If the process hasn't written any output in [NO_OUTPUT_TIMEOUT], then we'll check its alive state
 * and publish a [ProcessState] with its current alive state and its most recent line of output
 */
class ProcessStatePublisher(
    private val process: ProcessWrapper
) : StatusPublisher<ProcessState>() {
    private val tail: PublishingTail
    private var recurringProcessAliveTask: ScheduledFuture<*>? = null
    private var lastStatusUpdateTimestamp = AtomicLong(0)
    private val processRunningState: AliveState
        get() {
            return if (process.isAlive) {
                ProcessRunning()
            } else {
                ProcessExited(process.exitValue)
            }
        }

    companion object {
        private val NO_OUTPUT_TIMEOUT = Duration.ofSeconds(2)
    }

    init {
        tail = LoggingUtils.createPublishingTail(process.getOutput(), this::onProcessOutput)
        startProcessAliveChecks()
    }

    private fun onProcessOutput(output: String) {
        lastStatusUpdateTimestamp.set(System.currentTimeMillis())
        // In this event-driven process output handler (which is invoked for every line of the process' output
        // we read) we always denote the process' state as running.  The reason for this is that there is an out-of-sync
        // problem between the log line we read and the process' current state: the log lines will always be delayed
        // in comparison to the process' current state (running or exited).  Rather than be misleading and publish
        // a status with a state of exited but a log line that was written at some point when the process was still
        // running, we always state that the process is running here.  When the process actually is dead, it won't
        // write anymore and the timer-based check (from startProcessAliveChecks below) will get the process' last line
        // and publish its true running/exited state.  Note that this does not prevent other code from parsing an
        // output line that clearly denotes the process has exited and reacting appropriately.
        publishStatus(ProcessState(ProcessRunning(), output))
    }

    /**
     * We publish status updates based on every line the process writes to stdout.  But it's possible it could be
     * killed and no longer write anything, so we need to separately monitor if the process is alive or not.
     *
     * We'll publish an update on whether or not it's alive if we haven't based on the stdout checks for over 2
     * seconds.
     */
    private fun startProcessAliveChecks() {
        recurringProcessAliveTask = TaskPools.recurringTasksPool.scheduleAtFixedRate(2, TimeUnit.SECONDS, 5) {
            val timeSinceLastStatusUpdate =
                    Duration.ofMillis(System.currentTimeMillis() - lastStatusUpdateTimestamp.get())
            if (timeSinceLastStatusUpdate > Duration.ofSeconds(2)) {
                ProcessState(processRunningState, tail.mostRecentLine).also {
                    println("Process hasn't written in a while, pushing periodic update $it")
                    publishStatus(it)
                }
            }
        }
    }

    fun stop() {
        recurringProcessAliveTask?.cancel(true)
        //TODO: do we need to explicit stop the process output handling?
    }
}