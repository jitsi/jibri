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

package org.jitsi.jibri.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldNotBe

internal class AppDataTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        context("a json-encoded app data structure") {
            val appDataJsonStr = """
            {
                "file_recording_metadata":
                {
                    "upload_credentials":
                    {
                        "service_name":"dropbox",
                        "token":"XXXXXXXXYYYYYYYYYZZZZZZAAAAAAABBBBBBCCCDDD"
                    }
                }
            }
            """.trimIndent()
            should("be parsed correctly") {
                val appData = jacksonObjectMapper().readValue<AppData>(appDataJsonStr)
                appData.fileRecordingMetadata shouldNotBe null
                appData.fileRecordingMetadata?.shouldContainKey("upload_credentials")
                appData.fileRecordingMetadata?.get("upload_credentials") shouldNotBe null
                @Suppress("UNCHECKED_CAST")
                (appData.fileRecordingMetadata?.get("upload_credentials") as Map<Any, Any>)
                    .shouldContainExactly(mapOf<Any, Any>(
                        "service_name" to "dropbox",
                        "token" to "XXXXXXXXYYYYYYYYYZZZZZZAAAAAAABBBBBBCCCDDD"
                    ))
            }
        }
        context("a json-encoded app data structure with an extra top-level field") {
            val appDataJsonStr = """
            {
                "file_recording_metadata":
                {
                    "upload_credentials":
                    {
                        "service_name":"dropbox",
                        "token":"XXXXXXXXYYYYYYYYYZZZZZZAAAAAAABBBBBBCCCDDD"
                    }
                },
                "other_new_field": "hello"
            }
            """.trimIndent()
            should("be parsed correctly and ignore unknown fields") {
                val appData = jacksonObjectMapper().readValue<AppData>(appDataJsonStr)
                appData.fileRecordingMetadata shouldNotBe null
                appData.fileRecordingMetadata?.shouldContainKey("upload_credentials")
                appData.fileRecordingMetadata?.get("upload_credentials") shouldNotBe null
                @Suppress("UNCHECKED_CAST")
                (appData.fileRecordingMetadata?.get("upload_credentials") as Map<Any, Any>)
                    .shouldContainExactly(mapOf<Any, Any>(
                        "service_name" to "dropbox",
                        "token" to "XXXXXXXXYYYYYYYYYZZZZZZAAAAAAABBBBBBCCCDDD"
                    ))
            }
        }
    }
}
