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

package org.jitsi.jibri.service.impl

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.JibriIq
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.ffmpeg.ErrorScope
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.RECORDING_URL_OPTIONS
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStateMachine
import org.jitsi.jibri.service.toJibriServiceEvent
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.LoggingUtils
import org.jitsi.jibri.util.ProcessFactory
import org.jitsi.jibri.util.createIfDoesNotExist
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.logging.Logger

/**
 * Parameters needed for starting a [FileRecordingJibriService]
 */
data class FileRecordingParams(
    /**
     * Which call we'll join
     */
    val callParams: CallParams,
    /**
     * The ID of this session
     */
    val sessionId: String,
    /**
     * The login information needed to appear invisible in
     * the call
     */
    val callLoginParams: XmppCredentials,
    /**
     * The filesystem path to the script which should be executed when
     *  the recording is finished.
     */
    val finalizeScriptPath: Path,
    /**
     * The directory in which recordings should be created
     */
    val recordingDirectory: Path,
    /**
     * A map of arbitrary key, value metadata that will be written
     * to the metadata file.
     */
    val additionalMetadata: Map<Any, Any>? = null
)

/**
 * Set of metadata we'll put alongside the recording file(s)
 */
data class RecordingMetadata(
    @JsonProperty("meeting_url")
    val meetingUrl: String,
    val participants: List<Map<String, Any>>,
    @JsonIgnore val additionalMetadata: Map<Any, Any>? = null
) {
    /**
     * We tell the JSON serializer to ignore the additionalMetadata map (above)
     * and use this to expose each of its individual fields, that way they
     * are serialized at the top level (rather than being nested within a
     * 'additionalMetadata' JSON object)
     */
    @JsonAnyGetter
    fun get(): Map<Any, Any>? = additionalMetadata
}

/**
 * [FileRecordingJibriService] is the [JibriService] responsible for joining
 * a web call, capturing its audio and video, and writing that audio and video
 * to a file to be replayed later.
 */
class FileRecordingJibriService(
    private val fileRecordingParams: FileRecordingParams,
    private val jibriSelenium: JibriSelenium = JibriSelenium(),
    private val capturer: FfmpegCapturer = FfmpegCapturer(),
    private val processFactory: ProcessFactory = ProcessFactory()
) : JibriService() {
    /**
     * The [Logger] for this class
     */
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * The [Sink] this class will use to model the file on the filesystem
     */
    private var sink: Sink
    private val stateMachine = JibriServiceStateMachine()
    /**
     * The directory in which we'll store recordings for this particular session.  This is a directory that will
     * be nested within [FileRecordingParams.recordingDirectory].
     */
    private val sessionRecordingDirectory =
        fileRecordingParams.recordingDirectory.resolve(fileRecordingParams.sessionId)

    init {
        logger.info("Writing recording to $sessionRecordingDirectory")
        sink = FileSink(
            sessionRecordingDirectory,
            fileRecordingParams.callParams.callUrlInfo.callName
        )

        stateMachine.onStateTransition(this::onServiceStateChange)

        stateMachine.registerSubComponent(JibriSelenium.COMPONENT_ID)
        jibriSelenium.addStatusHandler { state ->
            stateMachine.transition(state.toJibriServiceEvent(JibriSelenium.COMPONENT_ID))
        }

        stateMachine.registerSubComponent(FfmpegCapturer.COMPONENT_ID)
        capturer.addStatusHandler { state ->
            stateMachine.transition(state.toJibriServiceEvent(FfmpegCapturer.COMPONENT_ID))
        }
    }

    private fun onServiceStateChange(@Suppress("UNUSED_PARAMETER") oldState: ComponentState, newState: ComponentState) {
        logger.info("Recording service transition from state $oldState to $newState")
        publishStatus(newState)
    }

    override fun start() {
        if (!createIfDoesNotExist(sessionRecordingDirectory, logger)) {
            publishStatus(ComponentState.Error(ErrorScope.SYSTEM, "Could not create recordings directory"))
        }
        if (!Files.isWritable(sessionRecordingDirectory)) {
            logger.error("Unable to write to ${fileRecordingParams.recordingDirectory}")
            publishStatus(ComponentState.Error(ErrorScope.SYSTEM, "Recordings directory is not writable"))
        }
        jibriSelenium.joinCall(
                fileRecordingParams.callParams.callUrlInfo.copy(urlParams = RECORDING_URL_OPTIONS),
                fileRecordingParams.callLoginParams)

        whenever(jibriSelenium).transitionsTo(ComponentState.Running) {
            logger.info("Selenium joined the call, starting the capturer")
            capturer.start(sink)
        }
        jibriSelenium.addToPresence("session_id", fileRecordingParams.sessionId)
        jibriSelenium.addToPresence("mode", JibriIq.RecordingMode.FILE.toString())
        jibriSelenium.sendPresence()
    }

    override fun stop() {
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        val participants = jibriSelenium.getParticipants()
        logger.info("Participants in this recording: $participants")
        if (Files.isWritable(sessionRecordingDirectory)) {
            val metadataFile = sessionRecordingDirectory.resolve("metadata.json")
            val metadata = RecordingMetadata(
                fileRecordingParams.callParams.callUrlInfo.callUrl,
                participants,
                fileRecordingParams.additionalMetadata
            )
            try {
                Files.newBufferedWriter(metadataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                    .use {
                        jacksonObjectMapper().writeValue(it, metadata)
                    }
            } catch (e: Exception) {
                logger.error("Error writing metadata", e)
                publishStatus(ComponentState.Error(ErrorScope.SYSTEM, "Could not write meeting metadata"))
            }
        } else {
            logger.error("Unable to write metadata file to recording directory ${fileRecordingParams.recordingDirectory}")
        }
        jibriSelenium.leaveCallAndQuitBrowser()
        logger.info("Finalizing the recording")
        finalize()
    }

    /**
     * Helper to execute the finalize script and wait for its completion.
     * NOTE that this will block for however long the finalize script takes
     * to complete (by design)
     */
    private fun finalize() {
        try {
            val finalizeCommand = listOf(
                fileRecordingParams.finalizeScriptPath.toString(),
                sessionRecordingDirectory.toString()
            )
            with(processFactory.createProcess(finalizeCommand)) {
                start()
                val streamDone = LoggingUtils.logOutput(this, logger)
                waitFor()
                // Make sure we get all the logs
                streamDone.get()
                logger.info("Recording finalize script finished with exit value $exitValue")
            }
        } catch (e: Exception) {
            logger.error("Failed to run finalize script", e)
        }
    }
}
