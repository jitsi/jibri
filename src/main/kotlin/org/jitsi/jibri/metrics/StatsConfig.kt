/*
 * Copyright @ 2024-Present 8x8, Inc
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
package org.jitsi.jibri.metrics

import org.jitsi.jibri.config.Config
import org.jitsi.metaconfig.config

object StatsConfig {
    val enableStatsD: Boolean by config {
        "JibriConfig::enableStatsD" { Config.legacyConfigSource.enabledStatsD!! }
        "jibri.stats.enable-stats-d".from(Config.configSource)
    }

    val statsdHost: String by config {
        "jibri.stats.host".from(Config.configSource)
    }

    val statsdPort: Int by config {
        "jibri.stats.port".from(Config.configSource)
    }

    val enablePrometheus: Boolean by config {
        "jibri.stats.prometheus.enabled".from(Config.configSource)
    }
}
