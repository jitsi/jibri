package org.jitsi.jibri

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
}