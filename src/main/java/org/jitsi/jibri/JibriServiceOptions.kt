package org.jitsi.jibri

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
         * The base url of the video service.  This assumes the call url
         * will be created via combining the [baseUrl] and [callName] options
         * like so: [baseUrl] + "/" + [callName].  The [baseUrl] is used to set
         * localstorage variables jibri relies on without having to fully
         * join the call.
         */
        val baseUrl: String,
        /**
         * The call name to join, see the comment above about the usage of
         * baseUrl and callName
         */
        val callName: String,
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
        val streamUrl: String? = null)