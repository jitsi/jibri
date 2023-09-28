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

package org.jitsi.jibri.capture.ffmpeg

import com.tinder.StateMachine
import org.jitsi.jibri.error.JibriError
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.util.NotifyingStateMachine

sealed class FfmpegEvent(val outputLine: String) {
    class EncodingLine(outputLine: String) : FfmpegEvent(outputLine)
    class ErrorLine(val error: JibriError) : FfmpegEvent(error.detail)
    class FinishLine(outputLine: String) : FfmpegEvent(outputLine)
    class OtherLine(outputLine: String) : FfmpegEvent(outputLine)

    /**
     * Used any time Ffmpeg has exited, regardless of what [outputLine] contains. However,
     * [error] will be set if [outputLine] contains an error, so that we may describe the
     * scope of that error.
     */
    class FfmpegExited(outputLine: String, val error: JibriError? = null) : FfmpegEvent(outputLine)
}

/**
 * To properly translate an [FfmpegOutputStatus] to an [FfmpegEvent], we need to take into
 * account what the status of the parsed line indicates, but also the current running state
 * of ffmpeg itself: if ffmpeg crashes, then its last output line may be "normal", but we need
 * to react to the fact that ffmpeg is no longer running.
 */
fun FfmpegOutputStatus.toFfmpegEvent(ffmpegStillRunning: Boolean): FfmpegEvent {
    return when (lineType) {
        OutputLineClassification.ENCODING -> {
            if (ffmpegStillRunning) {
                FfmpegEvent.EncodingLine(detail)
            } else {
                FfmpegEvent.FfmpegExited(detail, QuitUnexpectedly(detail))
            }
        }
        OutputLineClassification.UNKNOWN -> {
            if (ffmpegStillRunning) {
                FfmpegEvent.OtherLine(detail)
            } else {
                FfmpegEvent.FfmpegExited(detail, QuitUnexpectedly(detail))
            }
        }
        OutputLineClassification.FINISHED -> FfmpegEvent.FinishLine(detail)
        OutputLineClassification.ERROR -> {
            this as FfmpegErrorStatus
            FfmpegEvent.ErrorLine(this.error)
        }
    }
}

sealed class SideEffect

class FfmpegStatusStateMachine : NotifyingStateMachine() {
    private val stateMachine = StateMachine.create<ComponentState, FfmpegEvent, SideEffect> {
        initialState(ComponentState.StartingUp)

        state<ComponentState.StartingUp> {
            on<FfmpegEvent.EncodingLine> {
                transitionTo(ComponentState.Running)
            }
            on<FfmpegEvent.ErrorLine> {
                transitionTo(ComponentState.Error(it.error))
            }
            on<FfmpegEvent.FinishLine> {
                transitionTo(ComponentState.Finished)
            }
            on<FfmpegEvent.OtherLine> {
                dontTransition()
            }
            on<FfmpegEvent.FfmpegExited> {
                it.error?.let {
                    transitionTo(ComponentState.Error(it))
                } ?: transitionTo(ComponentState.Finished)
            }
        }

        state<ComponentState.Running> {
            on<FfmpegEvent.EncodingLine> {
                dontTransition()
            }
            on<FfmpegEvent.ErrorLine> {
                transitionTo(ComponentState.Error(it.error))
            }
            on<FfmpegEvent.FinishLine> {
                transitionTo(ComponentState.Finished)
            }
            on<FfmpegEvent.OtherLine> {
                dontTransition()
            }
            on<FfmpegEvent.FfmpegExited> {
                it.error?.let {
                    transitionTo(ComponentState.Error(it))
                } ?: transitionTo(ComponentState.Finished)
            }
        }

        state<ComponentState.Error> {
            on(any<FfmpegEvent>()) {
                dontTransition()
            }
        }

        state<ComponentState.Finished> {
            on(any<FfmpegEvent>()) {
                dontTransition()
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: run {
                throw Exception("Invalid state transition: $it")
            }
            if (validTransition.fromState::class != validTransition.toState::class) {
                notify(validTransition.fromState, validTransition.toState)
            }
        }
    }

    fun transition(event: FfmpegEvent): StateMachine.Transition<*, *, *> = stateMachine.transition(event)
}
