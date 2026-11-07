package com.djportablepro.app.midi.feedback

import com.djportablepro.app.core.DeckState
import com.djportablepro.app.state.GlobalState

class HerculesInpulse500FeedbackProfile : MidiFeedbackProfile {
    override fun getDeckFeedback(state: DeckState): List<Triple<Int, Int, Int>> = emptyList()
    override fun getGlobalFeedback(state: GlobalState): List<Triple<Int, Int, Int>> = emptyList()
}
