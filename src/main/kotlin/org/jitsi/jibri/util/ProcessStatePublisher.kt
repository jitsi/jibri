/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.jibri.util

import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.scheduleAtFixedRate
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

/**
 * [ProcessStatePublisher] is responsible for publishing a [ProcessState] every time:
 * 1) The process writes a line of output (to stdout/stderr)
 * 2) If the process hasn't written any output in [NO_OUTPUT_TIMEOUT], then we'll check its alive state
 * and publish a [ProcessState] with its current alive state and its most recent line of output
 */
class ProcessStatePublisher(
    private val name: String,
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
    private val logger = Logger.getLogger("${this::class.qualifiedName}.$name")

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
                logger.debug("Process $name hasn't written in 2 seconds, publishing periodic update")
                ProcessState(processRunningState, tail.mostRecentLine).also {
                    TaskPools.ioPool.submit {
                        // We fire all state transitions in the ioPool, otherwise we may try and cancel the
                        // processAliveChecks from within the thread it was executing in.  Another solution would've been
                        // to pass 'false' to recurringProcessAliveTask.cancel, but it felt cleaner to separate the threads
                        publishStatus(it)
                    }
                }
            }
        }
    }

    fun stop() {
        recurringProcessAliveTask?.cancel(true)
        // TODO: not calling 'tail.stop()' results in us processing ffmpeg's--for example--successful
        //  exit, which causes a 'finished' state update to propagate up and results in a duplicate 'stopService'
        //  call in JibriManager, since we have to call stopService after we receive finish (to handle the case
        //  of the service detecting an empty call, for example) and we can't distinguish a 'finished' state
        //  from an actual jibri service finishing on its own and the one that results from any call to
        //  'stop' when ffmpeg exits.  Calling 'tail.stop()' here fixes that behavior, but, i don't
        //  think that's what we want to do: instead we should be processing every log message a process
        //  writes to ensure that it exits correctly.  In addition to that, we should technically be modeling
        //  the 'stop' flow differently to be something more like: tell everything to stop and then *ensure*
        //  it all stopped cleanly and correctly and, if anything didn't, log an error (and perhaps update
        //  the health state)
    }
}
