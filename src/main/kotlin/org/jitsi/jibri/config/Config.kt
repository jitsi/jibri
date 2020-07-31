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

import com.typesafe.config.ConfigFactory
import org.jitsi.config.TypesafeConfigSource
import org.jitsi.metaconfig.ConfigSource
import org.jitsi.metaconfig.MapConfigSource

class Config {
    companion object {
        val ConfigFromFile = TypesafeConfigSource("config", ConfigFactory.load())

        /**
         * The 'new' config source
         */
        var configSource = ConfigFromFile

        /**
         * The 'legacy' config sources: we parsed a JSON file into a [JibriConfig] instance.
         * Unfortunately we can't parse the JSON file via the new config library because it
         * contains comments (which the new library doesn't support).
         */
        var legacyConfigSource = JibriConfig()

        /**
         * We also accepted config parameters via the command line, so this config source
         * is set to a [ConfigSource] containing those values.
         */
        var commandLineArgs: ConfigSource = MapConfigSource("command line args")
    }
}
