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

package org.jitsi.jibri.health

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import org.jitsi.jibri.status.JibriStatus

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class EnvironmentContext(
    private val name: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class JibriHealth(
    /**
     * Whether or not this Jibri is "busy". See [JibriManager#busy]
     */
    private val status: JibriStatus,
    /**
     * Context for the environment Jibri is currently active on
     * (only present if [busy] is true)
     */
    private val environmentContext: EnvironmentContext? = null
)
