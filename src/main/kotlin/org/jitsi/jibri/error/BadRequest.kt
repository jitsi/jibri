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
package org.jitsi.jibri.error

import org.jitsi.jibri.status.ErrorScope

/**
 * The request itself is invalid, for example because a parameter has a bad value. No Jibri instance can serve it, so
 * it must not be retried anywhere.
 *
 * The scope is [ErrorScope.SESSION] because this instance is still healthy: it refused the request, it did not fail.
 */
class BadRequest(detail: String) : JibriError(ErrorScope.SESSION, detail) {
    override fun shouldRetry(): Boolean = false
}

/**
 * Thrown when a start request is found to be invalid. It is thrown before a session is created, so that refusing a
 * request never costs a Jibri instance.
 *
 * [detail] describes what is wrong with the request. It is sent to the requester and written to its logs, so it must
 * never contain a credential such as a stream key.
 */
class BadRequestException(val detail: String) : Exception(detail)
