/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */

package org.jitsi.jibri.util

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KProperty

/**
 * A property delegate which recreates a value when it's accessed after having been
 * 'alive' for more than [timeout] via the given [creationFunc]
 */
class RefreshingProperty<T>(
    private val timeout: Duration,
    private val clock: Clock,
    private val creationFunc: () -> T?
) {
    constructor(timeout: Duration, creationFunc: () -> T?) : this(timeout, Clock.systemUTC(), creationFunc)

    private var value: T? = null
    private var valueCreationTimestamp: Instant? = null

    @Synchronized
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        val now = clock.instant()
        if (valueExpired(now)) {
            value = try {
                creationFunc()
            } catch (t: Throwable) {
                null
            }
            valueCreationTimestamp = now
        }
        return value
    }

    private fun valueExpired(now: Instant): Boolean {
        return value == null || Duration.between(valueCreationTimestamp, now) >= timeout
    }
}
