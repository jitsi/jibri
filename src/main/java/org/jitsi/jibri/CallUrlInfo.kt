package org.jitsi.jibri

import java.util.Objects

/**
 * We assume the 'baseUrl' represents a sort of landing page (on the same
 * domain) where we can set the necessary local storage values.  The call
 * url will be created by joining [baseUrl] and [callName] with a "/"
 */
data class CallUrlInfo(
        val baseUrl: String = "",
        val callName: String = ""
) {
    val callUrl = "$baseUrl/$callName"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false
        val otherCallUrlInfo = other as CallUrlInfo
        return hashCode() == otherCallUrlInfo.hashCode()
    }

    override fun hashCode(): Int {
        return Objects.hash(baseUrl.toLowerCase(), callName.toLowerCase())
    }
}
