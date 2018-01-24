package org.jitsi.jibri.service

import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.RecordingSinkType

/**
 * Contains the various configuration values needed when starting a recording
 */
data class JibriServiceOptions(
        /**
         * The sink type (where the captured media will be written to) for
         * this recording
         */
        val recordingSinkType: RecordingSinkType,
        /**
         * The url information for the call to be joined.
         */
        val callUrlInfo: CallUrlInfo,
        /**
         * Whether or not this session will connect using a sip gateway.  If
         * this value is true, the recordingSinkType and streamUrl values
         * will be ignored
         * TODO: would be nice to combine the vars in such a way that both
         * couldn't accidentally be set
         */
        val useSipGateway: Boolean = false,
        /**
         * If [recordingSinkType] is [RecordingSinkType.STREAM] then this
         *  parameter will contain the url to which the media should be
         *  streamed
         */
        val streamUrl: String? = null
)