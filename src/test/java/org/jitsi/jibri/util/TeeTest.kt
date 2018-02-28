package org.jitsi.jibri.util

import org.testng.AssertJUnit.assertEquals
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream

class TeeTest {
    private lateinit var outputStream: PipedOutputStream
    private lateinit var inputStream: PipedInputStream
    private lateinit var tee: Tee

    @BeforeMethod
    fun setUp() {
        outputStream = PipedOutputStream()
        inputStream = PipedInputStream(outputStream)
        tee = Tee(inputStream)
    }

    private fun pushIncomingData(data: ByteArray) {
        data.forEach {
            pushIncomingData(it)
        }
    }

    private fun pushIncomingData(data: Byte) {
        outputStream.write(data.toInt())
        tee.read()
    }

    @Test
    fun `test branch receives data`() {
        val branch = tee.addBranch()
        pushIncomingData(42.toByte())

        var read = branch.read()
        assertEquals(read, 42)

        pushIncomingData(43.toByte())

        read = branch.read()
        assertEquals(read, 43)
    }

    @Test
    fun `test branch receives string`() {
        val branch = tee.addBranch()
        pushIncomingData("hello, world\n".toByteArray())

        val reader = BufferedReader(InputStreamReader(branch))
        val read = reader.readLine()
        assertEquals(read, "hello, world")
    }

    @Test
    fun `test multiple branches receive data`() {
        val branch1 = tee.addBranch()
        val branch2 = tee.addBranch()

        pushIncomingData(42.toByte())
        var read = branch1.read()
        assertEquals(read, 42)
        read = branch2.read()
        assertEquals(read, 42)

        pushIncomingData(43.toByte())
        read = branch1.read()
        assertEquals(read, 43)
        read = branch2.read()
        assertEquals(read, 43)
    }

    @Test
    fun `test branch only receives data after its creation`() {
        pushIncomingData(42.toByte())

        val branch = tee.addBranch()
        pushIncomingData(43.toByte())
        val read = branch.read()
        assertEquals(read, 43)
    }
}