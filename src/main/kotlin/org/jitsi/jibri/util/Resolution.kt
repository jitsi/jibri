/*
 * Copyright @ 2026 - present 8x8, Inc.
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

/**
 * A size in pixels. It is written as "WIDTHxHEIGHT" everywhere we use it: in the config file, in an xrandr mode name
 * and in an ffmpeg argument.
 */
data class Resolution(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"

    companion object {
        private val REGEX = Regex("""(\d{1,5})x(\d{1,5})""")

        /** Reads a "WIDTHxHEIGHT" string, or returns null if [s] does not have that format. */
        fun parse(s: String): Resolution? = REGEX.matchEntire(s.trim())?.let {
            Resolution(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
    }
}
