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
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.ErrorScope
import org.jitsi.jibri.util.NotifyingStateMachine

sealed class FfmpegEvent(val outputLine: String) {
    class EncodingLine(outputLine: String) : FfmpegEvent(outputLine)
    class ErrorLine(val errorScope: ErrorScope, outputLine: String) : FfmpegEvent(outputLine)
    class FinishLine(outputLine: String) : FfmpegEvent(outputLine)
    class OtherLine(outputLine: String) : FfmpegEvent(outputLine)
}

fun FfmpegOutputStatus.toFfmpegEvent(): FfmpegEvent {
    return when (lineType) {
        OutputLineClassification.ENCODING -> FfmpegEvent.EncodingLine(detail)
        OutputLineClassification.UNKNOWN -> FfmpegEvent.OtherLine(detail)
        OutputLineClassification.FINISHED -> FfmpegEvent.FinishLine(detail)
        OutputLineClassification.ERROR -> {
            this as FfmpegErrorStatus
            FfmpegEvent.ErrorLine(this.errorScope, detail)
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
                transitionTo(ComponentState.Error(it.errorScope, it.outputLine))
            }
            on<FfmpegEvent.FinishLine> {
                transitionTo(ComponentState.Finished)
            }
            on<FfmpegEvent.OtherLine> {
                dontTransition()
            }
        }

        state<ComponentState.Running> {
            on<FfmpegEvent.EncodingLine> {
                dontTransition()
            }
            on<FfmpegEvent.ErrorLine> {
                transitionTo(ComponentState.Error(it.errorScope, it.outputLine))
            }
            on<FfmpegEvent.FinishLine> {
                transitionTo(ComponentState.Finished)
            }
            on<FfmpegEvent.OtherLine> {
                dontTransition()
            }
        }

        state<ComponentState.Error> {}

        state<ComponentState.Finished> {}

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
