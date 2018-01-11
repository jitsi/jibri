package org.jitsi

interface MonitorableProcess {
    fun isAlive(): Boolean
    fun getExitCode(): Int
}