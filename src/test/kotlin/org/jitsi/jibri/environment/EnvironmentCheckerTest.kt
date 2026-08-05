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

package org.jitsi.jibri.environment

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jitsi.jibri.status.ComponentHealthStatus
import org.jitsi.jibri.status.JibriStatusManager

class EnvironmentCheckerTest : ShouldSpec() {
    init {
        context("EnvironmentChecker") {
            context("on construction") {
                should("register as UNHEALTHY in the status manager") {
                    val statusManager = JibriStatusManager()
                    EnvironmentChecker(statusManager) { }
                    statusManager.overallStatus.health.healthStatus shouldBe ComponentHealthStatus.UNHEALTHY
                    statusManager.overallStatus.health.details[EnvironmentChecker.COMPONENT_ID]
                        ?.healthStatus shouldBe ComponentHealthStatus.UNHEALTHY
                }
            }

            context("validate") {
                should("transition to HEALTHY when ChromeDriver probe succeeds") {
                    val statusManager = JibriStatusManager()
                    val checker = EnvironmentChecker(statusManager) { }

                    statusManager.overallStatus.health.healthStatus shouldBe ComponentHealthStatus.UNHEALTHY

                    checker.validate(maxAttempts = 1, retryDelayMs = 0)

                    statusManager.overallStatus.health.healthStatus shouldBe ComponentHealthStatus.HEALTHY
                    statusManager.overallStatus.health.details[EnvironmentChecker.COMPONENT_ID]
                        ?.healthStatus shouldBe ComponentHealthStatus.HEALTHY
                }

                should("remain UNHEALTHY when ChromeDriver probe fails all attempts") {
                    val statusManager = JibriStatusManager()
                    val checker = EnvironmentChecker(statusManager) {
                        throw RuntimeException("ChromeDriver failed to start")
                    }

                    checker.validate(maxAttempts = 2, retryDelayMs = 0)

                    statusManager.overallStatus.health.healthStatus shouldBe ComponentHealthStatus.UNHEALTHY
                    statusManager.overallStatus.health.details[EnvironmentChecker.COMPONENT_ID]
                        ?.detail shouldBe "ChromeDriver failed to start after 2 attempts"
                }

                should("succeed on retry after initial failure") {
                    val statusManager = JibriStatusManager()
                    var callCount = 0
                    val checker = EnvironmentChecker(statusManager) {
                        callCount++
                        if (callCount == 1) {
                            throw RuntimeException("First attempt fails")
                        }
                    }

                    checker.validate(maxAttempts = 3, retryDelayMs = 0)

                    callCount shouldBe 2
                    statusManager.overallStatus.health.healthStatus shouldBe ComponentHealthStatus.HEALTHY
                }
            }
        }
    }
}
