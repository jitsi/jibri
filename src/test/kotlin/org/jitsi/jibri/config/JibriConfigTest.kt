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

package org.jitsi.jibri.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class JibriConfigTest : ShouldSpec({
    context("parsing a config") {
        context("with optional keys missing") {
            val config = jacksonObjectMapper().readValue<JibriConfig>(requiredOnly)
            should("use the default values") {
                config.jibriId shouldBe ""
                config.webhookSubscribers.shouldBeEmpty()
                config.singleUseMode shouldBe false
                config.enabledStatsD shouldBe true
            }
        }
    }
})


private val requiredOnly: String = """
    {
        "recording_directory": "some/recording/dir",
        "finalize_recording_script_path": "path/to/finalize",
        "xmpp_environments": []
    }
""".trimIndent()
