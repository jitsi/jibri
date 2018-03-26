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

import java.lang.reflect.Field

/**
 * Mimic the "pid" member of Java 9's [Process].  This can't be
 * an extension function as it gets called from a Java context
 * (which wouldn't see the extension function as a normal
 * member)
 */
fun pid(p: Process): Long {
    var pid: Long = -1

    try {
        if (p.javaClass.name.equals("java.lang.UNIXProcess")) {
            val field: Field = p.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            pid = field.getLong(p)
            field.isAccessible = false
        }
    } catch (e: Exception ) {
        pid = -1
    }
    return pid
}
