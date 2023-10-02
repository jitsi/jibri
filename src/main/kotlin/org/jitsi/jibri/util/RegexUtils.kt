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

// Regex definitions for parsing an ffmpeg output line
const val DIGIT = """\d"""
const val ONE_OR_MORE_DIGITS = "$DIGIT+"

// "1" is treated as a valid decimal (the decimal point and any trailing numbers are not required)
const val DECIMAL = """$ONE_OR_MORE_DIGITS(\.$ONE_OR_MORE_DIGITS)?"""
const val STRING = """[a-zA-Z]+"""
const val DATA_SIZE = "$ONE_OR_MORE_DIGITS$STRING"
const val TIMESTAMP = """$ONE_OR_MORE_DIGITS\:$ONE_OR_MORE_DIGITS\:$ONE_OR_MORE_DIGITS\.$ONE_OR_MORE_DIGITS"""
const val BITRATE = """$DECIMAL$STRING\/$STRING"""
const val SPEED = "${DECIMAL}x"
const val SPACE = """\s"""
const val NON_SPACE = """\S"""
const val ZERO_OR_MORE_SPACES = "$SPACE*"
const val ONE_OR_MORE_NON_SPACES = "$NON_SPACE+"
