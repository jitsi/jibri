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

import com.typesafe.config.Config
import org.jitsi.metaconfig.ConfigSource
import java.time.Duration
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class TypesafeConfigSource(override val name: String, private val config: Config) : ConfigSource {
    @ExperimentalStdlibApi
    override fun getterFor(type: KType): (String) -> Any {
        return when (type) {
            typeOf<Boolean>() -> { key -> config.getBoolean(key) }
            typeOf<Int>() -> { key -> config.getInt(key) }
            typeOf<Long>() -> { key -> config.getLong(key) }
            typeOf<String>() -> { key -> config.getString(key) }
            typeOf<List<String>>() -> { key -> config.getStringList(key) }
            typeOf<List<Int>>() -> { key -> config.getIntList(key) }
            typeOf<Duration>() -> { key -> config.getDuration(key) }

            else -> TODO("no support for type $type")
        }
    }
}
