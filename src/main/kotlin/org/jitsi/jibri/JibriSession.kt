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

package org.jitsi.jibri

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jitsi.jibri.job.IntermediateJobState
import org.jitsi.jibri.job.Running

/**
 * A [JibriSession] holds information about a session which is running and exposes the ability to:
 * 1) Cancel it
 * 2) Await its completion
 * 3) Take some action when it enters the 'Running' state.  'Running' implies that the session has
 *    successfully started.
 */
class JibriSession(
    private val deferred: Deferred<Unit>,
    private val intermediaryStateUpdates: StateFlow<IntermediateJobState>
) {
    /**
     * Await the completion of this session.  Should be surrounded by a try/catch, as all session completion is modeled
     * with exceptions
     * TODO: should we wrap this in JibriSession and expose a Result?
     */
    suspend fun await() = deferred.await()

    /**
     * Invoke the given [block] once the session moves to a [Running] state.
     */
    suspend fun onRunning(block: () -> Unit) {
        intermediaryStateUpdates.takeWhile { it != Running }.collect { }
        block()
    }

    fun cancel(reason: String) = deferred.cancel(reason)
}
