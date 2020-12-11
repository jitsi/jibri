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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.LoggerImpl
import java.util.logging.FileHandler

/**
 * Create a logger with [name] and all of its inherited
 * handlers cleared, adding only the given handler
 */
fun getLoggerWithHandler(name: String, handler: FileHandler): Logger {
    val logger = LoggerImpl(name)
    logger.setUseParentHandlers(false)
    logger.addHandler(handler)
    return logger
}

// We always want this to be in its own scope because we want to make sure we log everything
// from the process (though maybe we should have the caller launch it inside GlobalScope
// to make that more obvious?
fun logProcessOutput(process: ProcessWrapper, logger: Logger): Job {
    return GlobalScope.launch {
        process.output.takeWhile { it != EOF }.collect(logger::info)
    }
}
