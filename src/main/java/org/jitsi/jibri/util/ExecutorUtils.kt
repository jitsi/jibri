package org.jitsi.jibri.util

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

fun ScheduledExecutorService.scheduleAtFixedRate(
        delay: Duration = Duration(0, TimeUnit.SECONDS),
        period: Duration,
        action: Runnable) : ScheduledFuture<*> {
    return this.scheduleAtFixedRate(action, delay.unit.toMillis(delay.duration), period.duration, period.unit)
}