/*
 * Copyright @ 2026 - present 8x8, Inc.
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
package org.jitsi.jibri.util

import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import java.util.concurrent.TimeUnit

/** We could not give the X screen the resolution which a recording needs. */
class XrandrException(message: String) : Exception(message)

/**
 * Changes the resolution of the X screen that Chrome draws on and that ffmpeg captures.
 *
 * The X server is shared: jibri-xorg.service starts it once for the host, not once per recording. So a session which
 * needs another resolution sets it before Chrome starts, and sets it back when it ends. A session which leaves the
 * wrong resolution behind would spoil the next recording.
 *
 * The X server fixes the virtual screen size when it starts, and it cannot grow it later. A resolution therefore only
 * works if it is a Modeline in the X server config and is listed in the "Modes" of its Screen section.
 */
class Xrandr(
    parentLogger: Logger,
    private val display: String = ":0",
    private val runCommand: (List<String>) -> String = ::runAndReadOutput
) {
    private val logger = createChildLogger(parentLogger)

    /** The resolution of the X screen now, or null if we cannot read it. */
    fun currentResolution(): Resolution? {
        val output = query() ?: return null
        return CURRENT_REGEX.find(output)?.let {
            Resolution(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
    }

    /** The name of the connected output, for example "DUMMY0", or null if we cannot read it. */
    fun connectedOutput(): String? = query()?.let { CONNECTED_OUTPUT_REGEX.find(it)?.groupValues?.get(1) }

    /**
     * Gives the X screen [resolution], and checks that it worked.
     *
     * @throws XrandrException if the screen does not have [resolution] afterwards. We check rather than trust the
     * exit status, because a recording at the wrong size is worse than a failed one: nobody sees the problem until
     * the recording is needed.
     */
    fun setResolution(resolution: Resolution) {
        if (currentResolution() == resolution) {
            logger.info("The screen is already $resolution")
            return
        }

        val output = connectedOutput()
            ?: throw XrandrException("Found no connected output on display $display")
        logger.info("Setting the resolution of $output to $resolution")
        runCommand(listOf("xrandr", "--display", display, "--output", output, "--mode", resolution.toString()))

        val applied = currentResolution()
        if (applied != resolution) {
            throw XrandrException(
                "Asked for $resolution, but the screen is $applied. Add a Modeline for $resolution to the X server " +
                    "config, add it to the \"Modes\" of the Screen section, and restart the X server."
            )
        }
    }

    private fun query(): String? = try {
        runCommand(listOf("xrandr", "--display", display, "--current"))
    } catch (t: Throwable) {
        logger.error("Failed to run xrandr", t)
        null
    }

    companion object {
        private val CURRENT_REGEX = Regex("""current\s+(\d+)\s+x\s+(\d+)""")
        private val CONNECTED_OUTPUT_REGEX = Regex("""(?m)^(\S+)\s+connected""")
    }
}

/** Runs [command] and returns everything it writes, so that we can read a value out of it. */
private fun runAndReadOutput(command: List<String>): String {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw XrandrException("${command.first()} did not finish in $COMMAND_TIMEOUT_SECONDS seconds")
    }
    return output
}

private const val COMMAND_TIMEOUT_SECONDS = 10L
