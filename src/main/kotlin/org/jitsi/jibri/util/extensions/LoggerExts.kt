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

// Kotlin will warn that the inline keyword likely does nothing
// where we use it on the logger helper functions here, but
// we're not inlining for performance, we're inlining to make
// the logger helper functions transparent to java.util.logger
// so that the printed log message contains the correct function
// (i.e. the ACTUAL function the log call was made from, not the
// helpers here).
@file:Suppress("NOTHING_TO_INLINE")

package org.jitsi.jibri.util.extensions

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Extension functions to provide more friendly logging APIs
 */

inline fun Logger.error(msg: String) {
    this.log(Level.SEVERE, msg)
}

inline fun Logger.debug(msg: String) {
    this.fine(msg)
}
