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

package org.jitsi.jibri.capture.ffmpeg

import org.jitsi.jibri.sink.Sink

/**
 * [FfmpegCapturer] is responsible for launching ffmpeg, capturing from the
 * configured audio and video devices, and writing to the given [Sink]
 */
//class FfmpegCapturer(
//    osDetector: OsDetector = OsDetector(),
//    private val ffmpegExecutor: FfmpegExecutor = FfmpegExecutor()
//) : Capturer {
//    private val logger = Logger.getLogger(this::class.qualifiedName)
//    private val getCommand: (Sink) -> List<String>
//
//    init {
//        val osType = osDetector.getOsType()
//        logger.debug("Detected os as OS: $osType")
//        getCommand = when (osType) {
//            OsType.MAC -> { sink: Sink -> getFfmpegCommandMac(FfmpegExecutorParams(), sink) }
//            OsType.LINUX -> { sink: Sink -> getFfmpegCommandLinux(FfmpegExecutorParams(), sink) }
//            else -> throw UnsupportedOsException()
//        }
//    }
//
//    /**
//     * Start the capturer and write to the given [Sink].  Returns
//     * true on success, false otherwise
//     */
//    override fun start(sink: Sink): Boolean {
//        val command = getCommand(sink)
//        if (!ffmpegExecutor.launchFfmpeg(command)) {
//            return false
//        }
//        // Now make sure ffmpeg is actually healthy before returning that start
//        // was successful in case it starts up (and stays alive) but fails to
//        // start encoding successfully
//        for (i in 1..15) {
//            if (isHealthy()) {
//                return true
//            } else if (getExitCode() != null) {
//                logger.error("Ffmpeg already exited")
//                break
//            }
//            Thread.sleep(1000)
//        }
//        logger.error("Ffmpeg started up but did not start encoding after 15 tries, giving up")
//        return false
//    }
//
//    /**
//     * Returns true if the capturer is healthy, false otherwise
//     */
//    override fun isHealthy(): Boolean = ffmpegExecutor.isHealthy()
//
//    /**
//     * Returns the exit code if the capturer has exited, null if
//     * it's still running
//     */
//    override fun getExitCode(): Int? = ffmpegExecutor.getExitCode()
//
//    /**
//     * Stops the capturer
//     */
//    override fun stop() = ffmpegExecutor.stopFfmpeg()
//}
