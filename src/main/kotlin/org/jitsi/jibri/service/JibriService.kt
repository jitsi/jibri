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

package org.jitsi.jibri.service

import org.jitsi.jibri.util.StatusPublisher

enum class JibriServiceStatus {
    FINISHED,
    ERROR
}

typealias JibriServiceStatusHandler = (JibriServiceStatus) -> Unit

/**
 * Interface implemented by all implemented [JibriService]s.  A [JibriService]
 * is responsible for an entire feature of Jibri, such as recording a call
 * to a file or streaming a call to a url.
 */
abstract class JibriService : StatusPublisher<JibriServiceStatus>() {
    /**
     * Starts this [JibriService]
     */
    abstract fun start(): Boolean

    /**
     * Stops this [JibriService]
     */
    abstract fun stop()
}
