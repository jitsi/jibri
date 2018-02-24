package org.jitsi.jibri.util

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import kotlin.test.assertEquals

class ProcessMonitorTest {
    lateinit var mockProcess: MonitorableProcess
    lateinit var processMonitor: ProcessMonitor
    var exitCodes: MutableList<Int?> = mutableListOf()
    val deadCallback: (exitCode: Int?) -> Unit = { exitCode: Int? ->
        exitCodes.add(exitCode)
    }

    @BeforeMethod
    fun setUp() {
        exitCodes.clear()
        mockProcess = mock()
        processMonitor = ProcessMonitor(mockProcess, deadCallback)
    }

    @Test
    fun `test the process dying causes the callback to be invoked`() {
        whenever(mockProcess.isHealthy()).thenReturn(false)
        whenever(mockProcess.getExitCode()).thenReturn(42)

        processMonitor.run()
        assertEquals(1, exitCodes.size)
        assertEquals(42, exitCodes.first())

        processMonitor.run()
        assertEquals(2, exitCodes.size)
        assertEquals(42, exitCodes.last())
    }

    @Test
    fun `test passing a null exit code works`() {
        whenever(mockProcess.isHealthy()).thenReturn(false)
        whenever(mockProcess.getExitCode()).thenReturn(null)

        processMonitor.run()
        assertEquals(1, exitCodes.size)
        assertEquals(null, exitCodes.first())
    }

    @Test
    fun `test the callback is not invoked if the process is still alive`() {
        whenever(mockProcess.isHealthy()).thenReturn(true)
        for (i in 1..5) processMonitor.run()
        assertEquals(0, exitCodes.size)
    }
}