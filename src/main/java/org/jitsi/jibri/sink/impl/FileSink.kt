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
import org.jitsi.jibri.util.WriteableDirectory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * [FileSink] represents a sink which will write to a media file on the
 * filesystem
 */
class FileSink(recordingsDirectory: WriteableDirectory, callName: String, extension: String = ".mp4") : Sink {
    val file: File
    init {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val filename = "${callName}_${currentTime.format(formatter)}$extension"
        file = File(recordingsDirectory, filename)
    }

    /**
     * See [Sink.getPath]
     */
    override val path: String = file.path

    /**
     * See [Sink.getFormat]
     */
    override val format: String = file.extension

    /**
     * See [Sink.getOptions]
     */
    override val options: String = "-profile:v main -level 3.1"
}
