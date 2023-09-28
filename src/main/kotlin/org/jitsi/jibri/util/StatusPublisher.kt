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

package org.jitsi.jibri.util

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Class for publishing and subscribing to status.  Handles the management
 * of all subscribers (via [addStatusHandler]) and pushes updates to
 * those subscribers (synchronously) in [publishStatus].  Classes
 * interested in publishing their status should inherit from
 * [StatusPublisher].
 */
open class StatusPublisher<T> {
    private val handlers: MutableList<(T) -> Boolean> = CopyOnWriteArrayList()

    /**
     * Add a status handler for this [StatusPublisher].  Handlers
     * will be notified synchronously in the order they were added.
     */
    fun addStatusHandler(handler: (T) -> Unit) {
        handlers.add { status ->
            handler(status)
            true
        }
    }

    fun addTemporaryHandler(handler: (T) -> Boolean) {
        handlers.add(handler)
    }

    /**
     * The function a [StatusPublisher] subclass should call when it has
     * a new status to publish.  Note that handlers are notified synchronously
     * in the context of the thread which calls [publishStatus].
     */
    protected fun publishStatus(status: T) {
        // This will run all handlers, but only keep the ones that return true
        handlers.retainAll { it(status) }
    }
}
