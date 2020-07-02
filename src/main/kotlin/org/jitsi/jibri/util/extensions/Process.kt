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

package org.jitsi.jibri.util.extensions

import java.lang.reflect.Field

/**
 * Mimic the "pid" member of Java 9's [Process].
 */
val Process.pidValue: Long
    get() {
        var pid: Long = -1
        try {
            if (javaClass.name == "java.lang.UNIXProcess") {
                val field: Field = javaClass.getDeclaredField("pid")
                field.isAccessible = true
                pid = field.getLong(this)
                field.isAccessible = false
            }
        } catch (e: Exception) {
            pid = -1
        }
        return pid
    }
