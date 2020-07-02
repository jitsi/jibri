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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Stores task pools to be used for various tasks at a global level.  Globals are not great
 * for lots of reasons, but I think for the uses here they are appropriate.  Plus, the
 * fact that the variables here are 'vars' allows them to be overwritten by tests if
 * they want to sub in mock executors.
 */
class TaskPools {
    companion object {
        val DefaultIoPool: ExecutorService =
            Executors.newCachedThreadPool(NameableThreadFactory("IO Pool"))
        val DefaultRecurringTaskPool: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("Recurring Tasks Pool"))

        var ioPool: ExecutorService = DefaultIoPool
        var recurringTasksPool: ScheduledExecutorService = DefaultRecurringTaskPool
    }
}
