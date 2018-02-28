package org.jitsi.jibri.util.extensions

import org.jitsi.jibri.util.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * An extension function for [ScheduledExecutorService] which allows
 * the time to be passed as a [Duration] rather than the existing API
 * (which requires milliseconds)
 */
fun ScheduledExecutorService.scheduleAtFixedRate(
    delay: Long = 0,
    period: Long,
    unit: TimeUnit,
    action: () -> Unit): ScheduledFuture<*> {
    return this.scheduleAtFixedRate(action, delay, period, unit)
}

/**
 * A version of [ScheduledExecutorService.schedule] that takes a lambda
 * instead of requiring a Runnable (and moves it to the last argument)
 */
fun ScheduledExecutorService.schedule(
    delay: Long = 0,
    unit: TimeUnit = TimeUnit.SECONDS,
    action: () -> Unit): ScheduledFuture<*> {
    return this.schedule(action, delay, unit)
}