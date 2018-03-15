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
package org.jitsi.jibri;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author bbaldino
 * NOTE: this file had to be in java, I couldn't get it to recognize the
 * custom JsonCreator constructor to handle enum-case-agnosticisim in kotlin
 */
public enum RecordingSinkType
{
    STREAM("stream"),
    FILE("file"),
    //TODO: putting gateway in here doesn't feel great (and isn't what the xmpp uses anyway).
    // we need some top-level param that denotes what we're doing (recording a file,
    // streaming to youtube, or doing a sipgateway)
    GATEWAY("gateway");

    @SuppressWarnings("FieldCanBeLocal")
    private final String recordingSinkType;

    RecordingSinkType(final String recordingMode) {
        this.recordingSinkType = recordingMode;
    }

    @JsonCreator
    public static RecordingSinkType fromString(@JsonProperty("sinkType") final String text) {
        return RecordingSinkType.valueOf(text.toUpperCase());
    }
}
