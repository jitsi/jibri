package org.jitsi.jibri.util

interface MonitorableProcess {
    fun isAlive(): Boolean
    fun getExitCode(): Int?
}