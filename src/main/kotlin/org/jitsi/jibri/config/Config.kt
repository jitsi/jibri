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
import org.jitsi.config.ConfigSourceWrapper
import org.jitsi.config.TypesafeConfigSource
import org.jitsi.metaconfig.ConfigSource

class Config {
    companion object {
        private val ConfigFromFile = TypesafeConfigSource("config", ConfigFactory.load())

        /**
         * The 'new' config source
         */
        private val _configSource = ConfigSourceWrapper(ConfigFromFile)

        val configSource: ConfigSource
            get() = _configSource

        /**
         * Set [config] as the new config source, return the previously-set config source (so that it
         * can be restored later)
         */
        fun useDebugNewConfig(config: ConfigSource): ConfigSource {
            val oldConfig = _configSource.innerSource
            _configSource.innerSource = config
            return oldConfig
        }
    }
}
