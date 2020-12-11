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

// Regex definitions for parsing an ffmpeg output line
const val digit = """\d"""
const val oneOrMoreDigits = "$digit+"
// "1" is treated as a valid decimal (the decimal point and any trailing numbers are not required)
const val decimal = """$oneOrMoreDigits(\.$oneOrMoreDigits)?"""
const val string = """[a-zA-Z]+"""
const val dataSize = "$oneOrMoreDigits$string"
const val timestamp = """$oneOrMoreDigits\:$oneOrMoreDigits\:$oneOrMoreDigits\.$oneOrMoreDigits"""
const val bitrate = """$decimal$string\/$string"""
const val speed = "${decimal}x"
const val space = """\s"""
const val nonSpace = """\S"""
const val zeroOrMoreSpaces = "$space*"
const val oneOrMoreNonSpaces = "$nonSpace+"
