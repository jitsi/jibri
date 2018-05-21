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

package org.jitsi.jibri.sink.impl

import org.jitsi.jibri.sink.Sink
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * [FileSink] represents a sink which will write to a media file on the
 * filesystem
 */
class FileSink(recordingsDirectory: Path, callName: String, extension: String = ".mp4") : Sink {
    val file: Path
    init {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val filename = "${callName}_${currentTime.format(formatter)}$extension"
        file = recordingsDirectory.resolve(filename)
    }
    override val path: String = file.toString()
    override val format: String = file.toFile().extension
    override val options: Array<String> = arrayOf(
        "-profile:v", "main",
        "-level", "3.1"
    )
}
