/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.jitsi.jibri.statsd

const val ASPECT_START = "start"
const val ASPECT_STOP = "stop"
const val ASPECT_BUSY = "busy"
const val ASPECT_ERROR = "error"

const val XMPP_CONNECTED = "xmpp-connected"
const val XMPP_RECONNECTING = "xmpp-reconnecting"
const val XMPP_RECONNECTION_FAILED = "xmpp-reconnection-failed"
const val XMPP_PING_FAILED = "xmpp-ping-failed"
const val XMPP_CLOSED = "xmpp-closed"
const val XMPP_CLOSED_ON_ERROR = "xmpp-closed-on-error"

const val TAG_SERVICE_RECORDING = "recording"
const val TAG_SERVICE_LIVE_STREAM = "live_stream"
const val TAG_SERVICE_SIP_GATEWAY = "sip_gateway"
