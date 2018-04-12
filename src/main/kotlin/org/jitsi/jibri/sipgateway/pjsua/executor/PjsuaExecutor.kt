package org.jitsi.jibri.sipgateway.pjsua.executor

import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.PjsuaProcessWrapper
import org.jitsi.jibri.sipgateway.pjsua.PjsuaStatus
import org.jitsi.jibri.sipgateway.pjsua.util.PjsuaFileHandler
import org.jitsi.jibri.util.MonitorableProcess
import org.jitsi.jibri.util.NameableThreadFactory
import org.jitsi.jibri.util.extensions.debug
import org.jitsi.jibri.util.extensions.error
import org.jitsi.jibri.util.logStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(NameableThreadFactory("pjsua")),
    private val processBuilder: ProcessBuilder = ProcessBuilder()
) : MonitorableProcess {
    private val logger = Logger.getLogger(this::class.qualifiedName)
    private val pjsuaOutputLogger = Logger.getLogger("pjsua")
    /**
     * The currently active (if any) pjsua process
     */
    private var currentPjsuaProc: PjsuaProcessWrapper? = null

    init {
        pjsuaOutputLogger.useParentHandlers = false
        pjsuaOutputLogger.addHandler(PjsuaFileHandler())
    }
    /**
     * Launch pjsua with the given [PjsuaExecutorParams]
     */
    fun launchPjsua(pjsuaExecutorParams: PjsuaExecutorParams): Boolean {
        val command = listOf(
            "pjsua",
            "--capture-dev=$CAPTURE_DEVICE",
            "--playback-dev=$PLAYBACK_DEVICE",
            "--id", "${pjsuaExecutorParams.sipClientParams.displayName} <sip:jibri@127.0.0.1>",
            "--config-file", CONFIG_FILE_LOCATION,
            "--log-file", "/tmp/pjsua.out",
            "sip:${pjsuaExecutorParams.sipClientParams.sipAddress}"
        )

        try {
            currentPjsuaProc = PjsuaProcessWrapper(
                command,
                mapOf("DISPLAY" to X_DISPLAY),
                processBuilder)
        } catch (t: Throwable) {
            logger.error("Error starting pjsua: $t")
            return false
        }
        return currentPjsuaProc?.let {
            it.start()
            logStream(it.getOutput(), pjsuaOutputLogger, executor)
            true
        } ?: run {
            false
        }
    }

    /**
     * Shutdown pjsua gracefully
     */
    fun stopPjsua() {
        logger.info("Stopping pjsua process")
        currentPjsuaProc?.apply {
            stop()
            waitFor(10, TimeUnit.SECONDS)
            if (isAlive) {
                logger.error("Pjsua didn't stop, killing pjsua")
                destroyForcibly()
            }
        }
        logger.info("Pjsua exited with value ${currentPjsuaProc?.exitValue()}")
    }

    override fun getExitCode(): Int? {
        return currentPjsuaProc?.let {
            if (it.isAlive) null else it.exitValue()
        }
    }

    override fun isHealthy(): Boolean {
        return currentPjsuaProc?.let {
            val (status, mostRecentOutput) = it.getStatus()
            return@let when (status) {
                PjsuaStatus.HEALTHY -> {
                    logger.debug("Pjsua appears healthy: $mostRecentOutput")
                    true
                }
                PjsuaStatus.EXITED -> {
                    logger.debug("Pjsua exited with code ${getExitCode()}: $mostRecentOutput")
                    false
                }
            }
        } ?: run {
            false
        }
    }
}
