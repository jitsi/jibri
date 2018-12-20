package org.jitsi.jibri.capture.ffmpeg

import com.tinder.StateMachine
import org.jitsi.jibri.capture.ffmpeg.executor.ErrorScope
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegErrorStatus
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegOutputStatus
import org.jitsi.jibri.capture.ffmpeg.executor.OutputLineClassification
import org.jitsi.jibri.status.ComponentState

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

sealed class SideEffect {
}

class FfmpegStatusStateMachine {
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
        }

        state<ComponentState.Running> {
            on<FfmpegEvent.ErrorLine> {
                transitionTo(ComponentState.Error(it.errorScope, it.outputLine))
            }
            on<FfmpegEvent.FinishLine> {
                transitionTo(ComponentState.Finished)
            }
        }

        state<ComponentState.Error> {}

        state<ComponentState.Finished> {}

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            stateTranstionHandlers.forEach { handler ->
                handler(validTransition.fromState, validTransition.toState)
            }
        }
    }

    private val stateTranstionHandlers = mutableListOf<(ComponentState, ComponentState) -> Unit>()

    fun onStateTransition(handler: (ComponentState, ComponentState) -> Unit) {
        stateTranstionHandlers.add(handler)
    }

    fun transition(event: FfmpegEvent): StateMachine.Transition<*, *, *> = stateMachine.transition(event)

    init {
    }
}
