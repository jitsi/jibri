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

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.ShouldSpec

class CallUrlInfoTest : ShouldSpec() {
    init {
        "creating a CallUrlInfo" {
            val info = CallUrlInfo("baseUrl", "callName")
            should("assign the fields correctly") {
                info.baseUrl shouldBe "baseUrl"
                info.callName shouldBe "callName"
                info.callUrl shouldBe "baseUrl/callName"
            }
        }
        val info = CallUrlInfo("baseUrl", "callName")
        "the same CallUrlInfo instance" {
            should("equal itself") {
                info shouldBe info
                info.hashCode() shouldBe info.hashCode()
            }
        }
        "two equivalent CallUrlInfo instances" {
            val duplicateInfo = CallUrlInfo("baseUrl", "callName")
            should("be equal") {
                info shouldBe duplicateInfo
            }
            should("have the same hash code") {
                info.hashCode() shouldBe duplicateInfo.hashCode()
            }
        }
        "CallUrlInfo instances with different base urls" {
            val differentBaseUrl = CallUrlInfo("differentBaseUrl", "callName")
            should("not be equal") {
                info shouldNotBe differentBaseUrl
            }
            should("not have the same has code") {
                info.hashCode() shouldNotBe differentBaseUrl.hashCode()
            }
        }
        "CallUrlInfo instances with different call names" {
            val differentCallName = CallUrlInfo("differentUrl", "differentCallName")
            should("not be equal") {
                info shouldNotBe differentCallName
            }
            should("not have the same has code") {
                info.hashCode() shouldNotBe differentCallName.hashCode()
            }
        }
        "CallUrlInfo instances with different base url case" {
            val differentBaseUrlCase = CallUrlInfo("BASEURL", "callName")
            should("be equal") {
                info shouldBe differentBaseUrlCase
            }
            should("have the same has code") {
                info.hashCode() shouldBe differentBaseUrlCase.hashCode()
            }
        }
        "CallUrlInfo instances with different call name case" {
            val differentCallNameCase = CallUrlInfo("baseUrl", "CALLNAME")
            should("be equal") {
                info shouldBe differentCallNameCase
            }
            should("have the same has code") {
                info.hashCode() shouldBe differentCallNameCase.hashCode()
            }
        }

    }
}
