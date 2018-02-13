package org.jitsi.jibri.util

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import kotlin.test.assertEquals

class ProcessMonitorTest {
    lateinit var mockProcess: MonitorableProcess
    lateinit var processMonitor: ProcessMonitor
    var exitCodes: MutableList<Int?> = mutableListOf()
    val deadCallback: (exitCode: Int?) -> Unit = { exitCode: Int? ->
        exitCodes.add(exitCode)
    }

    @BeforeTest
    fun setUp() {
        mockProcess = mock()
        processMonitor = ProcessMonitor(mockProcess, deadCallback)
    }

    @Test
    fun testProcessDeadInvokesCallback() {
        whenever(mockProcess.isAlive()).thenReturn(false)
        whenever(mockProcess.getExitCode()).thenReturn(42)

        processMonitor.run()
        assertEquals(1, exitCodes.size)
        assertEquals(42, exitCodes.first())

        processMonitor.run()
        assertEquals(2, exitCodes.size)
        assertEquals(42, exitCodes.last())
    }

    @Test
    fun testProcessAliveNoCallback() {
        whenever(mockProcess.isAlive()).thenReturn(true)
        for (i in 1..5) processMonitor.run()
        assertEquals(0, exitCodes.size)
    }
}