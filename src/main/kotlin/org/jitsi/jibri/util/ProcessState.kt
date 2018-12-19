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
    override fun toString(): String = with (StringBuffer()) {
        append("${super.toString()} exit code: $exitCode")
        toString()
    }
}

enum class RunningStatus {
    /**
     * The component is currently 'running'.  Note that this does not mean it _should_ still be running, i.e. its
     * state could be [Status.FINISHED] and it's now ready to be shutdown cleanly.
     */
    RUNNING,
    /**
     * The process has exited
     */
    EXITED
}
