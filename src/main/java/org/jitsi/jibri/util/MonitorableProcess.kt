package org.jitsi.jibri.util

/**
 * [MonitorableProcess] provides an interface for monitoring the status
 * of a launched process which may die (e.g. a subprocess launched)
 */
interface MonitorableProcess {
    fun isAlive(): Boolean
    fun getExitCode(): Int?
}