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

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.logging.FileHandler
import java.util.logging.Logger

/**
 * A helper function to log a given [InputStream] via the given [Logger].
 * A future is returned which will be completed when the end of the given
 * stream is reached.
 */
fun logStream(
    stream: InputStream,
    logger: Logger,
    executor: ExecutorService = Executors.newSingleThreadExecutor(NameableThreadFactory("StreamLogger"))
): Future<Boolean> {
    return executor.submit(Callable<Boolean> {
        val reader = BufferedReader(InputStreamReader(stream))

        while (true) {
            val line = reader.readLine() ?: break
            logger.info(line)
        }

        return@Callable true
    })
}

/**
 * Create a logger with [name] and all of its inherited
 * handlers cleared, adding only the given handler
 */
fun getLoggerWithHandler(name: String, handler: FileHandler): Logger {
    val logger = Logger.getLogger(name)
    logger.useParentHandlers = false
    logger.addHandler(handler)
    return logger
}
