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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger

fun logStream(
    stream: InputStream,
    logger: Logger,
    executor: ExecutorService = Executors.newSingleThreadExecutor(NameableThreadFactory("StreamLogger"))
) {
    executor.submit {
        val reader = BufferedReader(InputStreamReader(stream))
        while (true) {
            logger.info(reader.readLine())
        }
    }
}
