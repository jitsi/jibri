package org.jitsi.jibri.sipgateway.pjsua.executor

import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.PjsuaFileHandler
import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.Tee
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.stopProcess
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

class PjsuaExecutor(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(NameableThreadFactory("pjsua"))
) : MonitorableProcess {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val pjsuaOutputLogger = Logger.getLogger("pjsua")
    /**
     * The currently active (if any) pjsua process
     */
    var currentPjsuaProc: Process? = null
    /**
     * In order to both analyze Pjsua's output live and log it to a file, we'll
     * tee its stdout stream so that both consumers can get all of its output.
     */
    private var pjsuaOutputTee: Tee? = null

    init {
        pjsuaOutputLogger.useParentHandlers = false
        pjsuaOutputLogger.addHandler(PjsuaFileHandler())
    }
    /**
     * Launch pjsua with the given [PjsuaExecutorParams]
     */
    fun launchPjsua(pjsuaExecutorParams: PjsuaExecutorParams): Boolean {
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
        try {
            currentPjsuaProc = pb.start()
        } catch (e: IOException) {
            logger.error("Error starting pjsua: $e")
            return false
        }
        currentPjsuaProc?.let {
            pjsuaOutputTee = Tee(it.inputStream)
        }
        pjsuaOutputTee?.let { tee ->
            executor.submit {
                val reader = BufferedReader(InputStreamReader(tee.addBranch()))
                while (true) {
                    pjsuaOutputLogger.info(reader.readLine())
                }
            }
        }
        logger.debug("Launched pjsua, is it alive? ${currentPjsuaProc?.isAlive}")
        return true
    }

    /**
     * Shutdown pjsua gracefully
     */
    fun stopPjsua() {
        stopProcess(currentPjsuaProc, "pjsua", logger)
    }

    override fun getExitCode(): Int? {
        currentPjsuaProc?.let {
            return if (it.isAlive) null else it.exitValue()
        }
        return null
    }

    override fun isHealthy(): Boolean {
        return true
    }
}
