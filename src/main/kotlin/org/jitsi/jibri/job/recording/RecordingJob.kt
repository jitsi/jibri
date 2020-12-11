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

package org.jitsi.jibri.job.recording

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jitsi.jibri.ErrorCreatingRecordingsDirectory
import org.jitsi.jibri.RecordingsDirectoryNotWritable
import org.jitsi.jibri.capture.ffmpeg.FfmpegHelpers
import org.jitsi.jibri.capture.ffmpeg.getFfmpegCommand
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.job.IntermediateJobState
import org.jitsi.jibri.job.JibriJob
import org.jitsi.jibri.job.Running
import org.jitsi.jibri.job.StartingUp
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.ObserverUrlOptions
import org.jitsi.jibri.selenium.Selenium
import org.jitsi.jibri.selenium.SeleniumFactory
import org.jitsi.jibri.selenium.SeleniumFactoryImpl
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.util.createIfDoesNotExist
import org.jitsi.jibri.util.customResource
import org.jitsi.jibri.util.withFfmpeg
import org.jitsi.jibri.util.withResource
import org.jitsi.jibri.util.withSelenium
import org.jitsi.jibri.util.withSubprocess
import org.jitsi.metaconfig.config
import org.jitsi.metaconfig.from
import org.jitsi.metaconfig.optionalconfig
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.xmpp.extensions.jibri.JibriIq
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class RecordingJob(
    parentLogger: Logger,
    private val sessionId: String,
    val callParams: CallParams,
    /**
     * A map of arbitrary key, value metadata that will be written
     * to the metadata file.
     */
    private val additionalMetadata: Map<Any, Any>? = null,
    private val seleniumFactory: SeleniumFactory = SeleniumFactoryImpl(),
    fileSystem: FileSystem = FileSystems.getDefault()
) : JibriJob {
    private val logger = createChildLogger(parentLogger, mapOf("session-id" to sessionId))
    private val _state = MutableStateFlow<IntermediateJobState>(StartingUp)
    override val state: StateFlow<IntermediateJobState> = _state.asStateFlow()
    override val name: String = "Recording job $sessionId"
    private val recordingsDirectory: String by config("jibri.recording.recordings-directory".from(Config.configSource))
    private val finalizeScriptPath: String?
        by optionalconfig("jibri.recording.finalize-script".from(Config.configSource))

    /**
     * The directory in which we'll store recordings for this particular session.  This is a directory that will
     * be nested within [recordingsDirectory].
     */
    private val sessionRecordingDirectory =
        fileSystem.getPath(recordingsDirectory).resolve(sessionId)

    private val sink = FileSink(
        sessionRecordingDirectory,
        callParams.callUrlInfo.callName
    )

    init {
        logger.info("Writing recording to $sessionRecordingDirectory, finalize script " +
            "path ${finalizeScriptPath ?: "not set"}")
        try {
            createIfDoesNotExist(sessionRecordingDirectory)
        } catch (t: Throwable) {
            throw ErrorCreatingRecordingsDirectory(t, sessionRecordingDirectory)
        }

        if (!Files.isWritable(sessionRecordingDirectory)) {
            throw RecordingsDirectoryNotWritable(sessionRecordingDirectory.toAbsolutePath().toString())
        }
    }

    /**
     * Runs this [RecordingJob]
     */
    override suspend fun run() {
        logger.info("Running")
        withSelenium(seleniumFactory.create(logger)) { selenium ->
            selenium.joinCall(
                callParams.callUrlInfo.copy(urlParams = ObserverUrlOptions),
                callParams.callLogin
            )

            selenium.addToPresence("session_id", sessionId)
            selenium.addToPresence("mode", JibriIq.RecordingMode.FILE.toString())
            selenium.sendPresence()

            val ffmpegCommand = getFfmpegCommand(sink)
            logger.info("Starting ffmpeg via ${ffmpegCommand.joinToString(separator = " ")} ($ffmpegCommand)")
            withFfmpeg(ffmpegCommand) { ffmpeg ->
                val recording = customResource(this) {
                    it.writeMetadata(selenium)
                    it.finalize()
                }
                withResource(recording) {
                    coroutineScope {
                        launch {
                            FfmpegHelpers.onEncodingStart(ffmpeg) {
                                logger.info("Ffmpeg started successfully")
                                _state.value = Running
                            }
                        }
                        launch { FfmpegHelpers.watchForProcessError(ffmpeg) }
                        launch { selenium.monitorCall() }
                    }
                }
            }
        }
    }

    /**
     * Helper to execute the finalize script and wait for its completion.
     * NOTE that this will block for however long the finalize script takes
     * to complete (by design)
     */
    private fun finalize() {
        val scriptPath = finalizeScriptPath ?: run {
            logger.info("No finalize script set")
            return
        }
        val finalizeCommand = listOf(scriptPath, sessionRecordingDirectory.toString())
        logger.info("Running finalize command ${finalizeCommand.joinToString(separator = " ")}")
        try {
            withSubprocess(finalizeCommand, logger) { finalize ->
                finalize.waitFor()
                logger.info("Recording finalize script finished with exit value ${finalize.exitValue}")
            }
        } catch (t: Throwable) {
            logger.error("Failed to run finalize script", t)
            // Note: we don't throw here as this is a cleanup method, and therefore will always be
            // run in the context of some other exception having been thrown, so throwing here would
            // mean the exception gets lost anyways
        }
    }

    private fun writeMetadata(selenium: Selenium) {
        val participants = try {
            selenium.getParticipants()
        } catch (t: Throwable) {
            logger.error(
                "An error occurred while trying to get the participants list, proceeding with " +
                    "an empty participants list",
                t
            )
            listOf()
        }

        logger.info("Participants in this recording: $participants")
        if (Files.isWritable(sessionRecordingDirectory)) {
            val metadataFile = sessionRecordingDirectory.resolve("metadata.json")
            val metadata = RecordingMetadata(
                callParams.callUrlInfo.callUrl,
                participants,
                additionalMetadata
            )
            try {
                Files.newBufferedWriter(metadataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                    .use {
                        jacksonObjectMapper().writeValue(it, metadata)
                    }
            } catch (t: Throwable) {
                logger.error("Error writing meeting metadata", t)
                // Note: we don't throw here as this is a cleanup method, and therefore will always be
                // run in the context of some other exception having been thrown, so throwing here would
                // mean the exception gets lost anyways
            }
        } else {
            logger.error("Unable to write metadata file to recording directory $recordingsDirectory")
        }
    }
}
