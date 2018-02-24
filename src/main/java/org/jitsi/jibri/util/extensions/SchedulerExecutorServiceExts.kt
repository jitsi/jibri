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
    action: Runnable): ScheduledFuture<*> {
    return this.scheduleAtFixedRate(action, delay, period, unit)
}