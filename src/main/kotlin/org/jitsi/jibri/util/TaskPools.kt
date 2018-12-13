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
        var ioPool: ExecutorService =
                Executors.newCachedThreadPool(NameableThreadFactory("IO Pool"))
        var recurringTasksPool: ScheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(NameableThreadFactory("Recurring Tasks Pool"))
    }
}