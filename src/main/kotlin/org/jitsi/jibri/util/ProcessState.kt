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

/**
 * Models 2 things about a process:
 * 1) Whether or not it is 'alive' (vs. having exited) and, if it has exited, what its exit code was
 * 2) Its most recent line of output (via stdout/stderr)
 */
data class ProcessState(
    val runningState: AliveState,
    val mostRecentOutput: String
)

/**
 * Models whether a process is alive or has exited
 */
sealed class AliveState(val runningStatus: RunningStatus) {
    override fun toString(): String = with(StringBuffer()) {
        append("$runningStatus")
        toString()
    }
}

class ProcessRunning : AliveState(RunningStatus.RUNNING)

class ProcessExited(val exitCode: Int) : AliveState(RunningStatus.EXITED) {
    override fun toString(): String = buildString {
        append("${super.toString()} exit code: $exitCode")
    }
}

class ProcessFailedToStart() : AliveState(RunningStatus.FAILED)

enum class RunningStatus {
    /**
     * The component is currently 'running'.  Note that this does not mean it _should_ still be running, i.e. its
     * state could be [Status.FINISHED] and it's now ready to be shutdown cleanly.
     */
    RUNNING,
    /**
     * The process has exited
     */
    EXITED,
    /**
     * The process failed to start
     */
    FAILED
}
