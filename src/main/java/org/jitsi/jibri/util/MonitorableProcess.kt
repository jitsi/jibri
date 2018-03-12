package org.jitsi.jibri.util

/**
 * [MonitorableProcess] provides an interface for monitoring the status
 * of a launched process which may die (e.g. a subprocess launched)
 */
interface MonitorableProcess {
    /**
     * Returns [true] if this [MonitorableProcess] considers
     * itself "healthy", [false] otherwise
     */
    fun isHealthy(): Boolean
    /**
     * Return the exit code of this [MonitorableProcess],
     * or [null] if it's still alive
     */
    fun getExitCode(): Int?
}
