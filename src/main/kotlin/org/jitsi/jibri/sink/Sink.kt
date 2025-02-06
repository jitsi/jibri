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

package org.jitsi.jibri.sink

/**
 * [Sink] describes a class which data will be 'written to'.  It contains
 * a destination (via [path]), a format (via [format]) and a set
 * of options which each [Sink] implementation may provide.
 * TODO: currently this is modeled as generic, but really it's an
 * "FfmpegSink", so maybe it should be named as such?
 */
interface Sink {
    /**
     * The path to which this [Sink] has been designated to write
     */
    val path: String
}
