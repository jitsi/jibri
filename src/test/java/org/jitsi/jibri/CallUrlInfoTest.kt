package org.jitsi.jibri

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class CallUrlInfoTest {
    @Test
    fun `test creation`() {
        val info = CallUrlInfo("baseUrl", "callName")
        assertEquals("baseUrl", info.baseUrl)
        assertEquals("callName", info.callName)
        assertEquals("baseUrl/callName", info.callUrl)
    }

    @Test
    fun `test equals() and hashCode()`() {
        val info = CallUrlInfo("baseUrl", "callName")
        val duplicateInfo = CallUrlInfo("baseUrl", "callName")
        val differentBaseUrl = CallUrlInfo("differentBaseUrl", "callName")
        val differentCallName = CallUrlInfo("differentUrl", "differentCallName")
        val differentBaseUrlCase = CallUrlInfo("BASEURL", "callName")
        val differentCallNameCase = CallUrlInfo("baseUrl", "CALLNAME")

        assertTrue(info == info)
        assertTrue(info.hashCode() == info.hashCode())

        assertTrue(info == duplicateInfo)
        assertTrue(info.hashCode() == duplicateInfo.hashCode())

        assertFalse(info == differentBaseUrl)
        assertFalse(info.hashCode() == differentBaseUrl.hashCode())

        assertFalse(info == differentCallName)
        assertFalse(info.hashCode() == differentCallName.hashCode())

        assertTrue(info == differentBaseUrlCase)
        assertTrue(info.hashCode() == differentBaseUrlCase.hashCode())

        assertTrue(info == differentCallNameCase)
        assertTrue(info.hashCode() == differentCallNameCase.hashCode())
    }
}
