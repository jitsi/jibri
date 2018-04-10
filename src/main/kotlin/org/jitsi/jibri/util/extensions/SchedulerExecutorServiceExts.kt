/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
 *
 */

package org.jitsi.jibri.util.extensions

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A version of [ScheduledExecutorService.scheduleAtFixedRate] that takes
 * a lambda instead of requiring a [Runnable] (and moves it to the last
 * argument)
 */
fun ScheduledExecutorService.scheduleAtFixedRate(
    period: Long,
    unit: TimeUnit,
    delay: Long = 0,
    action: () -> Unit
): ScheduledFuture<*> {
    return this.scheduleAtFixedRate(action, delay, period, unit)
}

/**
 * A version of [ScheduledExecutorService.schedule] that takes a lambda
 * instead of requiring a [Runnable] (and moves it to the last argument)
 */
fun ScheduledExecutorService.schedule(
    delay: Long = 0,
    unit: TimeUnit = TimeUnit.SECONDS,
    action: () -> Unit
): ScheduledFuture<*> {
    return this.schedule(action, delay, unit)
}
