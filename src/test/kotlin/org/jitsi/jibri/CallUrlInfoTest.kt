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

package org.jitsi.jibri

import io.kotlintest.IsolationMode
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.ShouldSpec
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table

class CallUrlInfoTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    init {
        "creating a CallUrlInfo" {
            "without url params" {
                val info = CallUrlInfo("baseUrl", "callName")
                should("assign the fields correctly") {
                    info.baseUrl shouldBe "baseUrl"
                    info.callName shouldBe "callName"
                    info.callUrl shouldBe "baseUrl/callName"
                }
            }
            "with url params" {
                val info = CallUrlInfo("baseUrl", "callName", listOf("one", "two", "three"))
                should("assign the fields correctly") {
                    info.baseUrl shouldBe "baseUrl"
                    info.callName shouldBe "callName"
                    info.callUrl shouldBe "baseUrl/callName#one&two&three"
                }
            }
        }
        "a nullable CallUrlInfo instance" {
            should("not equal null") {
                val nullableInfo: CallUrlInfo? = CallUrlInfo("baseUrl", "callName")
                nullableInfo shouldNotBe null
            }
        }
        "equality and hashcode" {
            val info = CallUrlInfo("baseUrl", "callName")
            "a CallUrlInfo instance" {
                should("not equal another type") {
                    info.equals("string") shouldBe false
                }
                "when compared to other variations" {
                    should("be equal/not equal where appropriate") {
                        val duplicateInfo = CallUrlInfo("baseUrl", "callName")
                        val differentBaseUrl = CallUrlInfo("differentBaseUrl", "callName")
                        val differentCallName = CallUrlInfo("differentUrl", "differentCallName")
                        val differentBaseUrlCase = CallUrlInfo("BASEURL", "callName")
                        val differentCallNameCase = CallUrlInfo("baseUrl", "CALLNAME")
                        val withUrlParams = CallUrlInfo("baseUrl", "callName", listOf("one", "two", "three"))

                        val t = table(
                            headers("left", "right", "shouldEqual"),
                            row(info, info, true),
                            row(info, duplicateInfo, true),
                            row(info, differentBaseUrl, false),
                            row(info, differentCallName, false),
                            row(info, differentBaseUrlCase, true),
                            row(info, differentCallNameCase, true),
                            row(info, withUrlParams, true)
                        )
                        forAll(t) { left, right, shouldEqual ->
                            if (shouldEqual) {
                                left shouldBe right
                                left.hashCode() shouldBe right.hashCode()
                            } else {
                                left shouldNotBe right
                                left.hashCode() shouldNotBe right.hashCode()
                            }
                        }
                    }
                }
            }
        }
    }
}
