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

import java.io.File
import java.io.IOException

/**
 * A [File] representing a directory described by [dirPath]
 * which it requires to be writable.  The directory doesn't need to
 * exist ([WriteableDirectory] will try and create it) but it must
 * be able to be created and, once created, must be able to be
 * written to.  If any of these conditions fails, the constructor
 * throws [IOException].
 */
class WriteableDirectory(dirPath: String) : File(dirPath) {
    init {
        val path = File(dirPath)
        if ((!path.isDirectory or !path.mkdirs()) and !path.canWrite()) {
            throw IOException("Unable to write to directory $dirPath")
        }
    }
}
