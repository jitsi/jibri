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

import org.jitsi.jibri.config.Config
import org.jitsi.jibri.sink.Sink
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * [FileSink] represents a sink which will write to a media file on the
 * filesystem.  A maximum length of 125 characters is enforced for the filename.
 * NOTE: I considered letting the maximum length be configurable, but we require that we
 * are able to append a timestamp to the filename, so we can't give the caller full control
 * over the value anyway.  Because of that I just made the value hard-coded.
 */
class FileSink(recordingsDirectory: Path, callName: String) : Sink {
    val file: Path
    init {
        val suffix = "_${LocalDateTime.now().format(TIMESTAMP_FORMATTER)}.$recordingExtension"
        val filename = "${callName.take(MAX_FILENAME_LENGTH - suffix.length)}$suffix"
        file = recordingsDirectory.resolve(filename)
    }
    override val path: String = file.toString()

    companion object {
        val recordingExtension: String by config("jibri.ffmpeg.recording-extension".from(Config.configSource))
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        const val MAX_FILENAME_LENGTH = 125
    }
}
