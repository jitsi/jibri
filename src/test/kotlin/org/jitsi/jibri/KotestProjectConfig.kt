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

package org.jitsi.jibri

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.listeners.Listener
import io.kotest.extensions.junitxml.JunitXmlReporter

class KotestProjectConfig : AbstractProjectConfig() {
    override fun listeners(): List<Listener> = listOf(
        /**
         * The JunitXmlReporter writes a junit5 compatible unit test output
         * but with the full scope of tests as their name, unlike the default
         * one which only includes the 'should' block from kotest tests as
         * the name.  See https://kotest.io/docs/extensions/junit_xml.html.
         */
        JunitXmlReporter(
            includeContainers = false,
            useTestPathAsName = true,
            outputDir = "full-test-name-test-results"
        )
    )
}
