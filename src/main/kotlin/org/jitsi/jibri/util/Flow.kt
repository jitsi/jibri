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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration

/**
 * Emits the result of [default] if no element has been emitted after [timeout] since the previous
 * element.  Does this only "once per emitted element" (i.e. will not repeatedly emit the default
 * value if no element arrives).  The default will never be emitted if the underlying flow never
 * emits an element.
 */
@ExperimentalCoroutinesApi
fun <T> Flow<T>.ifNoDataFor(timeout: Duration, default: () -> T): Flow<T> {
    return transformLatest {
        emit(it)
        delay(timeout.toMillis())
        emit(default())
    }
}
