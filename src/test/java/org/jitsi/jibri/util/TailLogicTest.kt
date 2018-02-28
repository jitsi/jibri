package org.jitsi.jibri.util

import org.testng.AssertJUnit.assertEquals
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class TailLogicTest {
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var tail: TailLogic

    @BeforeMethod
    fun setUp() {
        outputStream = PipedOutputStream()
        inputStream = PipedInputStream(outputStream)
        tail = TailLogic(inputStream)
    }

    private fun writeLine(data: ByteArray) {
        data.forEach {
            outputStream.write(it.toInt())
        }
    }

    @Test
    fun `test initialized state`() {
        assertEquals("", tail.mostRecentLine)
    }

    @Test
    fun `test writing line`() {
        writeLine("hello, world\n".toByteArray())
        tail.readLine()

        assertEquals("hello, world", tail.mostRecentLine)
    }

    @Test
    fun `test the last line is returned`() {
        writeLine("hello, world\n".toByteArray())
        tail.readLine()
        writeLine("goodbye, world\n".toByteArray())
        tail.readLine()

        assertEquals("goodbye, world", tail.mostRecentLine)
    }
}