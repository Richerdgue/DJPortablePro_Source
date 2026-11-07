package com.djportablepro.app.midi.feedback

import com.djportablepro.app.core.DeckState
import com.djportablepro.app.state.GlobalState

interface MidiFeedbackProfile {
    fun getDeckFeedback(state: DeckState): List<Triple<Int, Int, Int>>
    fun getGlobalFeedback(state: GlobalState): List<Triple<Int, Int, Int>>
}
