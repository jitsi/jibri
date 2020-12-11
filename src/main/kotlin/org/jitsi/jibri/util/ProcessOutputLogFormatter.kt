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

package org.jitsi.jibri.util

import java.text.DecimalFormat
import java.util.Calendar
import java.util.logging.Formatter
import java.util.logging.LogRecord

class ProcessOutputLogFormatter : Formatter() {
    override fun format(record: LogRecord?): String {
        return buildString {
            with(Calendar.getInstance()) {
                append(get(Calendar.YEAR)).append("-")
                append(twoDigFmt.format(get(Calendar.MONTH) + 1)).append("-")
                append(twoDigFmt.format(get(Calendar.DAY_OF_MONTH)))
                append(" ")
                append(twoDigFmt.format(get(Calendar.HOUR_OF_DAY))).append(":")
                append(twoDigFmt.format(get(Calendar.MINUTE))).append(":")
                append(twoDigFmt.format(get(Calendar.SECOND))).append(".")
                append(threeDigFmt.format(get(Calendar.MILLISECOND)))
            }
            append(" ")
            appendLine(record?.message)
        }
    }

    companion object {
        private val twoDigFmt = DecimalFormat("00")
        private val threeDigFmt = DecimalFormat("000")
    }
}
