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

import com.typesafe.config.ConfigFactory
import org.jitsi.config.TypesafeConfigSource
import org.jitsi.jibri.config.Config
import org.jitsi.metaconfig.ConfigSource
import org.jitsi.metaconfig.MetaconfigSettings

/**
 * A helper function to apply a given config for the duration of a scope and then restore the previous
 * config afterwards
 */
class ConfigSubstitution : AutoCloseable {
    private var originalConfig: ConfigSource? = null

    fun setConfig(configStr: String, useDefaults: Boolean = true) {
        val config = if (useDefaults) {
            ConfigFactory.parseString(configStr).withFallback(ConfigFactory.load())
        } else {
            ConfigFactory.parseString(configStr)
        }
        setConfig(TypesafeConfigSource("fake", config))
    }

    fun setConfig(config: ConfigSource) {
        MetaconfigSettings.cacheEnabled = false
        originalConfig = Config.useDebugNewConfig(config)
    }

    override fun close() {
        originalConfig?.let {
            Config.useDebugNewConfig(it)
            MetaconfigSettings.cacheEnabled = true
        }
    }
}

suspend fun withConfig(configStr: String, block: suspend () -> Unit) {
    ConfigSubstitution().use {
        it.setConfig(configStr)
        block()
    }
}
