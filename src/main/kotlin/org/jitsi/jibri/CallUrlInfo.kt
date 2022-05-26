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

package org.jitsi.jibri

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.Objects

/**
 * We assume the 'baseUrl' represents a sort of landing page (on the same
 * domain) where we can set the necessary local storage values.  The call
 * url will be created by joining [baseUrl] and [callName] with a "/".  If
 * set, a list of [urlParams] will be concatenated after the call name with
 * a "#" in between.
 */
data class CallUrlInfo(
    val baseUrl: String = "",
    val callName: String = "",
    private val urlParams: List<String> = listOf()
) {
    @get:JsonIgnore
    val callUrl: String
        get() {
            return if (urlParams.isNotEmpty()) {
                "$baseUrl/$callName#${urlParams.joinToString("&")}"
            } else {
                "$baseUrl/$callName"
            }
        }

    override fun equals(other: Any?): Boolean {
        return when {
            other == null -> false
            this === other -> true
            javaClass != other.javaClass -> false
            else -> hashCode() == other.hashCode()
        }
    }

    override fun hashCode(): Int {
        // Purposefully ignore urlParams here
        return Objects.hash(baseUrl.lowercase(), callName.lowercase())
    }
}
