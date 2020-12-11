/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 */

package org.jitsi.jibri.selenium

/**
 * URL options common to all Jibri job types
 */
val CommonJibriUrlOptions = listOf(
    "config.iAmRecorder=true",
    "config.analytics.disabled=true",
    "config.p2p.enabled=false",
    "config.prejoinPageEnabled=false",
)

/**
 * For things like recording and streaming, where the Jibri only
 * receives media
 */
val ObserverUrlOptions = CommonJibriUrlOptions + listOf(
    "config.externalConnectUrl=null",
    "config.startWithAudioMuted=true",
    "config.startWithVideoMuted=true",
    "interfaceConfig.APP_NAME=\"Jibri\"",
    "config.requireDisplayName=false"
)

val SipGatewayUrlOptions = CommonJibriUrlOptions + listOf(
    "config.iAmSipGateway=true",
    "config.ignoreStartMuted=true",
    "config.requireDisplayName=false"
)
