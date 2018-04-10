package org.jitsi.jibri.sipgateway.pjsua.executor

import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.util.stopProcess
import java.util.logging.Logger

data class PjsuaExecutorParams(
    val sipClientParams: SipClientParams,
    /**
     * The name of the screen session we'll start when
     * launching pjsua
     */
    val screenSessionName: String = "pjsua"
)

private const val CAPTURE_DEVICE = 23
private const val PLAYBACK_DEVICE = 24
private const val CONFIG_FILE_LOCATION = "/home/jibri/pjsua.config"
private const val X_DISPLAY = ":1"

class PjsuaExecutor : MonitorableProcess {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    /**
     * The currently active (if any) pjsua process
     */
    var currentPjsuaProc: Process? = null
    /**
     * Launch pjsua with the given [PjsuaExecutorParams]
     */
    fun launchPjsua(pjsuaExecutorParams: PjsuaExecutorParams) {
        //TODO: not able to format this as nicely because we need arguments like
        // "--id xx xx" to be parsed as 2 separate arguments when passing them
        // to the shell ("--id" is one, "xx xx" is another); splitting on spaces
        // doesn't work because the value might have a space in it.  look into
        // a nicer way to do this?
        val pjsuaCommand = """
            pjsua
            --capture-dev=$CAPTURE_DEVICE
            --playback-dev=$PLAYBACK_DEVICE
            --id
            ${pjsuaExecutorParams.sipClientParams.displayName} <sip:jibri@127.0.0.1>
            --config-file
            $CONFIG_FILE_LOCATION
            --log-file
            /tmp/pjsua.out
            sip:${pjsuaExecutorParams.sipClientParams.sipAddress}
        """.trimIndent()

        val command = pjsuaCommand.split("\n")
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        pb.environment().put("DISPLAY", X_DISPLAY)

        logger.info("Running pjsua command:\n ${pb.command()}")
        currentPjsuaProc = pb.start()
    }

    /**
     * Shutdown pjsua gracefully
     */
    fun stopPjsua() {
        stopProcess(currentPjsuaProc, "pjsua", logger)
    }

    override fun getExitCode(): Int? {
        //TODO: this will actually be the exit code of screen
        currentPjsuaProc?.let {
            return if (it.isAlive) null else it.exitValue()
        }
        return null
    }

    override fun isHealthy(): Boolean {
        return true
    }
}
