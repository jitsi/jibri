package org.jitsi.jibri.sipgateway.pjsua

import org.jitsi.jibri.sipgateway.SipClient
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.sipgateway.pjsua.executor.PjsuaExecutor
import org.jitsi.jibri.sipgateway.pjsua.executor.PjsuaExecutorParams

data class PjsuaClientParams(
    val sipClientParams: SipClientParams
)

class PjsuaClient(private val pjsuaClientParams: PjsuaClientParams) : SipClient {
    private val pjsuaExecutor = PjsuaExecutor()

    override fun start(): Boolean {
        return pjsuaExecutor.launchPjsua(PjsuaExecutorParams(pjsuaClientParams.sipClientParams))
    }

    override fun stop() {
        pjsuaExecutor.stopPjsua()
    }

    override fun getExitCode(): Int? = pjsuaExecutor.getExitCode()

    override fun isHealthy(): Boolean = pjsuaExecutor.isHealthy()
}
