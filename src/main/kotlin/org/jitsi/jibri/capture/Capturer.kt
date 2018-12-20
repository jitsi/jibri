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

package org.jitsi.jibri.capture

import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.sink.Sink

class UnsupportedOsException(override var message: String = "Jibri does not support this OS") : Exception()

/**
 * [Capturer] represents a process which will capture media.  It implements
 * [MonitorableProcess] so that its state can be monitored so it can be
 * restarted if it dies.
 */
interface Capturer {
    /**
     * Start the capturer with the given [Sink].  Returns
     * true if the [Capturer] was started successfully,
     * false otherwise.
     */
    fun start(sink: Sink)

    /**
     * Stop the capturer
     */
    fun stop()
}
