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
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.capture.ffmpeg.executor.FFMPEG_RESTART_ATTEMPTS
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.RECORDING_URL_OPTIONS
import org.jitsi.jibri.service.JibriService
import org.jitsi.jibri.service.JibriServiceStatus
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.sink.impl.FileSink
import org.jitsi.jibri.util.ProcessFactory
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.createIfDoesNotExist
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.logStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
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
    private val executor: ScheduledExecutorService,
    private val jibriSelenium: JibriSelenium = JibriSelenium(executor = executor),
    private val capturer: Capturer = FfmpegCapturer(),
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
    /**
     * The handle to the scheduled process monitor task, which we use to
     * cancel the task
     */
    private var processMonitorTask: ScheduledFuture<*>? = null
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
        jibriSelenium.addStatusHandler(this::publishStatus)
    }

    override fun start(): Boolean {
        if (!createIfDoesNotExist(sessionRecordingDirectory, logger)) {
            return false
        }
        if (!Files.isWritable(sessionRecordingDirectory)) {
            logger.error("Unable to write to ${fileRecordingParams.recordingDirectory}")
            return false
        }
        if (!jibriSelenium.joinCall(
                fileRecordingParams.callParams.callUrlInfo.copy(urlParams = RECORDING_URL_OPTIONS),
                fileRecordingParams.callLoginParams)
        ) {
            logger.error("Selenium failed to join the call")
            return false
        }
        if (!capturer.start(sink)) {
            logger.error("Capturer failed to start")
            return false
        }
        jibriSelenium.addToPresence("session_id", fileRecordingParams.sessionId)
        jibriSelenium.addToPresence("mode", JibriIq.RecordingMode.FILE.toString())
        jibriSelenium.sendPresence()
        val processMonitor = createCaptureMonitor(capturer)
        processMonitorTask = executor.scheduleAtFixedRate(processMonitor, 30, 10, TimeUnit.SECONDS)
        return true
    }

    private fun createCaptureMonitor(process: Capturer): ProcessMonitor {
        var numRestarts = 0
        return ProcessMonitor(process) { exitCode ->
            if (exitCode != null) {
                logger.error("Capturer process is no longer healthy.  It exited with code $exitCode")
            } else {
                logger.error("Capturer process is no longer healthy but it is still running, stopping it now")
            }
            if (numRestarts == FFMPEG_RESTART_ATTEMPTS) {
                logger.error("Giving up on restarting the capturer")
                publishStatus(JibriServiceStatus.ERROR)
            } else {
                logger.info("Trying to restart capturer")
                numRestarts++
                // Re-create the sink here because we want a new filename
                // TODO: we can run into an issue here where this takes a while and the monitor task runs again
                // and, while ffmpeg is still starting up, detects it as 'not encoding' for the second time
                // and shuts it down.  Add a forced delay to match the initial delay we set when
                // creating the monitor task?
                sink = FileSink(sessionRecordingDirectory, fileRecordingParams.callParams.callUrlInfo.callName)
                process.stop()
                if (!process.start(sink)) {
                    logger.error("Capture failed to restart, giving up")
                    publishStatus(JibriServiceStatus.ERROR)
                }
            }
        }
    }

    override fun stop() {
        processMonitorTask?.cancel(false)
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
            Files.newBufferedWriter(metadataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use {
                jacksonObjectMapper().writeValue(it, metadata)
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
                val streamDone = logStream(getOutput(), logger)
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
