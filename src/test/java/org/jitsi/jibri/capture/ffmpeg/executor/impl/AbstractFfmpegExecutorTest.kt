package org.jitsi.jibri.capture.ffmpeg.executor.impl

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.sink.Sink
import org.junit.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class FakeFfmpegExecutor(fakeProcessBuilder: ProcessBuilder) : AbstractFfmpegExecutor(fakeProcessBuilder) {
    override fun getFfmpegCommand(ffmpegExecutorParams: FfmpegExecutorParams, sink: Sink): String = ""
}

class AbstractFfmpegExecutorTest {
    private val mockSink: Sink = mock()
    private val mockProcessBuilder: ProcessBuilder = mock()
    private val mockProcess: Process = mock()
    private lateinit var processStdOutWriter: PipedOutputStream
    private lateinit var mockProcessStdOut: InputStream
    private lateinit var ffmpegExecutor: AbstractFfmpegExecutor

    private val mocks = listOf(
        mockSink,
        mockProcessBuilder,
        mockProcess
    )

    @BeforeMethod
    fun setUp() {
        ffmpegExecutor = FakeFfmpegExecutor(mockProcessBuilder)
        processStdOutWriter = PipedOutputStream()
        mockProcessStdOut = PipedInputStream(processStdOutWriter)
        mocks.forEach { reset(it) }

        whenever(mockProcessBuilder.start()).thenReturn(mockProcess)
        whenever(mockProcess.inputStream).thenReturn(mockProcessStdOut)
    }

    @Test
    fun `test if the process was never launched, then 'null' is returned as the exit code`() {
        assertNull(ffmpegExecutor.getExitCode())
    }

    @Test
    fun `test if the process is alive, then 'null' is returned as the exit code`() {
        whenever(mockProcess.isAlive).thenReturn(true)

        ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), mockSink)
        assertNull(ffmpegExecutor.getExitCode())
    }

    @Test
    fun `test if the process is dead, then its exit code is returned`() {
        whenever(mockProcess.isAlive).thenReturn(true)
        ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), mockSink)
        whenever(mockProcess.exitValue()).thenReturn(42)
        whenever(mockProcess.isAlive).thenReturn(false)
        assertEquals(42, ffmpegExecutor.getExitCode())
    }

    @Test
    fun `test if the process was never launched, then it doesn't show as healthy`() {
        assertFalse(ffmpegExecutor.isHealthy())
    }
}
