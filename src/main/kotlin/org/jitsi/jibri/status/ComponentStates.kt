package org.jitsi.jibri.status

import org.jitsi.jibri.capture.ffmpeg.executor.ErrorScope

sealed class ComponentState {
    object StartingUp : ComponentState() {
        override fun toString(): String = "Starting up"
    }
    object Running : ComponentState() {
        override fun toString(): String = "Running"
    }
    //TODO: i think we need to keep the states as singletons (best practice to keep them singletons
    // so comparing them works correctly i think?).  how could we store this state?  or, does it make
    // sense that not all 'error' states are equal?  (but even if they had the same values, the wouldn't
    // be equal.  of course we could always override equals for this? --> we could just check that
    // they are of the same type?)
    class Error(val errorScope: ErrorScope, val detail: String) : ComponentState() {
        override fun toString(): String = "Error: $errorScope $detail"
    }
    object Finished : ComponentState() {
        override fun toString(): String = "Finished"
    }
}