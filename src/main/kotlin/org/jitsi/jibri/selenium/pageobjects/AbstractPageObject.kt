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

package org.jitsi.jibri.selenium.pageobjects

import org.jitsi.utils.logging2.createLogger
import org.openqa.selenium.remote.RemoteWebDriver
import kotlin.time.measureTime

/**
 * [AbstractPageObject] is a page object class containing logic common to
 * all page object instances
 */
open class AbstractPageObject(protected val driver: RemoteWebDriver) {
    private val logger = createLogger()

    open fun visit(url: String): Boolean {
        logger.info("Visiting url $url")

        val totalTime = measureTime {
            driver.get(url)
        }

        logger.info("Waited $totalTime for driver to load page")
        return true
    }
}
