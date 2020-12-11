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

import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.recording.RecordingJob
import org.jitsi.jibri.job.sipgateway.SipGatewayJob
import org.jitsi.jibri.job.streaming.StreamingJob
import org.jitsi.jibri.job.streaming.StreamingParams
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.SeleniumFactory
import org.jitsi.jibri.selenium.SeleniumFactoryImpl
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.utils.logging2.Logger
import java.nio.file.FileSystem
import java.nio.file.FileSystems

interface JobFactory {
    fun createRecordingJob(
        parentLogger: Logger,
        sessionId: String,
        callParams: CallParams,
        additionalMetadata: Map<Any, Any>? = null,
        seleniumFactory: SeleniumFactory = SeleniumFactoryImpl(),
        fileSystem: FileSystem = FileSystems.getDefault()
    ): JibriJob

    fun createStreamingJob(
        parentLogger: Logger,
        sessionId: String,
        callParams: CallParams,
        streamingParams: StreamingParams
    ): JibriJob

    fun createSipGwJob(
        parentLogger: Logger,
        sessionId: String,
        callParams: CallParams,
        sipClientParams: SipClientParams,
    ): JibriJob
}

object JobFactoryImpl : JobFactory {
    override fun createRecordingJob(
        parentLogger: Logger,
        sessionId: String,
        callParams: CallParams,
        additionalMetadata: Map<Any, Any>?,
        seleniumFactory: SeleniumFactory,
        fileSystem: FileSystem
    ): JibriJob {
        return RecordingJob(
            parentLogger,
            sessionId,
            callParams,
            additionalMetadata,
            seleniumFactory,
            fileSystem
        )
    }

    override fun createStreamingJob(
        parentLogger: Logger,
        sessionId: String,
        callParams: CallParams,
        streamingParams: StreamingParams
    ): JibriJob {
        return StreamingJob(parentLogger, sessionId, callParams, streamingParams)
    }

    override fun createSipGwJob(
        parentLogger: Logger,
        sessionId: String,
        callParams: CallParams,
        sipClientParams: SipClientParams,
    ): JibriJob {
        return SipGatewayJob(parentLogger, sessionId, callParams, sipClientParams)
    }
}
