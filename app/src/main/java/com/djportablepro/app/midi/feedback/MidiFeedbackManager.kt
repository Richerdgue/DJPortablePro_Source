package com.djportablepro.app.midi.feedback

import com.djportablepro.app.midi.usb.UsbMidiManager
import com.djportablepro.app.state.DjStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observed application state and sends MIDI feedback to the hardware.
 * Fully decoupled from logical state and audio processing.
 */
class MidiFeedbackManager(
    private val stateManager: DjStateManager,
    private val usbMidiManager: UsbMidiManager,
    private val profile: MidiFeedbackProfile,
    private val scope: CoroutineScope
) {
    init {
        // Observe Deck A
        scope.launch {
            stateManager.deckAState.collectLatest { state ->
                profile.getDeckFeedback(state).forEach { (s, d1, d2) ->
                    usbMidiManager.sendMidi(s, d1, d2)
                }
            }
        }

        // Observe Deck B
        scope.launch {
            stateManager.deckBState.collectLatest { state ->
                profile.getDeckFeedback(state).forEach { (s, d1, d2) ->
                    usbMidiManager.sendMidi(s, d1, d2)
                }
            }
        }

        // Observe Global State
        scope.launch {
            stateManager.globalState.collectLatest { state ->
                profile.getGlobalFeedback(state).forEach { (s, d1, d2) ->
                    usbMidiManager.sendMidi(s, d1, d2)
                }
            }
        }
    }
}
