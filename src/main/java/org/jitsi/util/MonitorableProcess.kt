package org.jitsi.util

interface MonitorableProcess {
    fun isAlive(): Boolean
    fun getExitCode(): Int?
}