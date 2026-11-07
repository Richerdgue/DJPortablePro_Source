package com.djportablepro.app.state

import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import com.djportablepro.app.logging.DjLogger
import com.djportablepro.app.core.Deck
import com.djportablepro.app.core.DeckState
import com.djportablepro.app.core.JogSource
import com.djportablepro.app.midi.mapping.DjAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the logical state of the application by observing DJ actions.
 */
class DjStateManager(
    private val eventBus: DjEventBus,
    private val audioPlayer: NativeAudioPlayer?,
    private val scope: CoroutineScope
) {
    private val _deckAState = MutableStateFlow(DeckState(Deck.DECK_A))
    val deckAState = _deckAState.asStateFlow()

    private val _deckBState = MutableStateFlow(DeckState(Deck.DECK_B))
    val deckBState = _deckBState.asStateFlow()

    private val _globalState = MutableStateFlow(GlobalState())
    val globalState = _globalState.asStateFlow()

    init {
        eventBus.addListener { action ->
            DjLogger.d("DjStateManager", "Processing action: $action")
            processAction(action)
        }
    }

    private fun processAction(action: DjAction) {
        when (action) {
            is DjAction.PlayPause -> {
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.togglePlayPause(deckIdx)
                updateDeck(action.deck) { it.copy(isPlaying = !it.isPlaying) }
            }
            is DjAction.Cue -> {
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.seekTo(deckIdx, 0L)
                updateDeck(action.deck) { it.copy(isCueActive = true) }
            }
            is DjAction.Sync -> {
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.toggleSync(deckIdx)
                updateDeck(action.deck) { it.copy(isSyncActive = !it.isSyncActive) }
            }
            is DjAction.Pfl -> {
                updateDeck(action.deck) { it.copy(isPflActive = !it.isPflActive) }
            }
            
            is DjAction.Volume -> {
                val volume = action.value / 127.0f
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setDeckVolume(deckIdx, volume)
                updateDeck(action.deck) { it.copy(volume = action.value) }
            }
            is DjAction.Gain -> {
                val gain = (action.value / 64.0f).coerceIn(0.0f, 2.0f)
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setGain(deckIdx, gain)
                updateDeck(action.deck) {
                    val newState = it.copy(gain = action.value)
                    syncDsp(action.deck, newState)
                    newState
                }
            }
            is DjAction.High -> {
                val high = (action.value / 64.0f).coerceIn(0.0f, 2.0f)
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setEqHigh(deckIdx, high)
                updateDeck(action.deck) {
                    val newState = it.copy(high = action.value)
                    syncDsp(action.deck, newState)
                    newState
                }
            }
            is DjAction.Mid -> {
                val mid = (action.value / 64.0f).coerceIn(0.0f, 2.0f)
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setEqMid(deckIdx, mid)
                updateDeck(action.deck) {
                    val newState = it.copy(mid = action.value)
                    syncDsp(action.deck, newState)
                    newState
                }
            }
            is DjAction.Low -> {
                val low = (action.value / 64.0f).coerceIn(0.0f, 2.0f)
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setEqLow(deckIdx, low)
                updateDeck(action.deck) {
                    val newState = it.copy(low = action.value)
                    syncDsp(action.deck, newState)
                    newState
                }
            }
            is DjAction.Filter -> {
                val filter = (action.value / 127.0f).coerceIn(0.0f, 1.0f)
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setFilter(deckIdx, filter)
                updateDeck(action.deck) {
                    val newState = it.copy(filter = action.value)
                    syncDsp(action.deck, newState)
                    newState
                }
            }
            is DjAction.Pitch -> {
                // Invert physical Pitch Fader so UP is - (slower) and DOWN is + (faster)
                val norm = (8192 - action.value) / 8192.0f
                val rate = (1.0f + norm * 0.50f).coerceIn(0.50f, 1.50f)
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                DjPlayerController.setPlaybackRate(deckIdx, rate, isManualUserAction = true)
                updateDeck(action.deck) { it.copy(pitch = action.value) }
            }
            
            is DjAction.Shift -> updateDeck(action.deck) { it.copy(isShiftPressed = action.pressed) }
            is DjAction.Vinyl -> {
                val currentVal = if (action.deck == Deck.DECK_A) _deckAState.value.isVinylMode else _deckBState.value.isVinylMode
                updateDeck(action.deck) { it.copy(isVinylMode = !currentVal) }
            }
            is DjAction.Slip -> updateDeck(action.deck) { it.copy(isSlipMode = action.enabled) }
            is DjAction.Quantize -> updateDeck(action.deck) { it.copy(isQuantizeEnabled = action.enabled) }
            is DjAction.Keylock -> updateDeck(action.deck) { it.copy(isKeylockEnabled = action.enabled) }
            
            is DjAction.JogTouch -> {
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                val state = if (action.deck == Deck.DECK_A) _deckAState.value else _deckBState.value
                
                if (action.source == JogSource.PLATTER) {
                    DjPlayerController.setPlatterTouched(deckIdx, action.touched)
                    if (state.isVinylMode) {
                        audioPlayer?.setScratchMode(deckIdx, action.touched)
                    }
                    updateDeck(action.deck) { it.copy(isPlatterTouched = action.touched) }
                } else {
                    updateDeck(action.deck) { it.copy(isRingTouched = action.touched) }
                }
            }
            is DjAction.JogMove -> {
                val deckIdx = if (action.deck == Deck.DECK_A) 0 else 1
                val state = if (action.deck == Deck.DECK_A) _deckAState.value else _deckBState.value
                
                // Rotar el jog wheel de la interfaz en tiempo real al girar el jog físico
                DjPlayerController.onPhysicalJogMoved(deckIdx, action.delta)

                if (action.source == JogSource.PLATTER) {
                    DjPlayerController.keepPlatterTouchAlive(deckIdx)
                }

                if (state.isVinylMode && state.isPlatterTouched && action.source == JogSource.PLATTER) {
                    val velocity = action.delta / 10.0f
                    DjPlayerController.scratch(deckIdx, velocity)
                    audioPlayer?.setScratchVelocity(deckIdx, velocity)
                } else {
                    // Pitch Bend (Nudge) mode
                    DjPlayerController.jogNudge(deckIdx, action.delta)
                }
                updateDeck(action.deck) { it.copy(jogDelta = action.delta) }
            }
            
            is DjAction.PerformanceMode -> updateDeck(action.deck) { it.copy(performanceMode = action.mode) }
            is DjAction.VuMeter -> {
                if (action.deck != null) {
                    updateDeck(action.deck) { it.copy(vuLevel = action.value) }
                } else {
                    _globalState.value = _globalState.value.copy(masterVuLevel = action.value)
                }
            }

            is DjAction.Crossfader -> {
                val pos = action.value / 127.0f
                DjPlayerController.setCrossfader(pos)
                audioPlayer?.setCrossfader(pos)
                _globalState.value = _globalState.value.copy(crossfader = action.value)
            }
            is DjAction.AutoDjToggle -> _globalState.value = _globalState.value.copy(isAutoDjEnabled = !_globalState.value.isAutoDjEnabled)
            
            is DjAction.Load -> DjPlayerController.handleLoadAction(action.deck)
            is DjAction.BrowsePush -> DjPlayerController.handleBrowsePush()
            is DjAction.BrowseEncoder -> DjPlayerController.handleBrowseEncoder(action.delta)

            else -> { /* Other actions */ }
        }
    }

    private fun syncDsp(deck: Deck, state: DeckState) {
        val deckIdx = if (deck == Deck.DECK_A) NativeAudioPlayer.DECK_A else NativeAudioPlayer.DECK_B
        // Map 0-127 MIDI to 0.0-2.0 multiplier (1.0 = 0dB center)
        val l = (state.low / 64.0f)
        val m = (state.mid / 64.0f)
        val h = (state.high / 64.0f)
        val g = (state.gain / 64.0f)
        // Map 0-127 filter to 0.0 (LPF) -> 0.5 (Off) -> 1.0 (HPF)
        val f = state.filter / 127.0f
        
        audioPlayer?.setDspParams(deckIdx, l, m, h, f, g)
    }

    fun updateAnalysis(deck: Deck, bpm: Double, phase: Double, downbeatPhase: Double, confidence: Double = 0.0) {
        updateDeck(deck) {
            it.copy(
                bpm = bpm.toFloat(),
                beatPhase = phase.toFloat(),
                downbeatPhase = downbeatPhase.toFloat(),
                confidence = confidence.toFloat(),
            )
        }
    }

    private fun updateDeck(deck: Deck, transform: (DeckState) -> DeckState) {
        when (deck) {
            Deck.DECK_A -> _deckAState.value = transform(_deckAState.value)
            Deck.DECK_B -> _deckBState.value = transform(_deckBState.value)
        }
    }
}

data class GlobalState(
    val crossfader: Int = 64,
    val masterVuLevel: Int = 0,
    val isAutoDjEnabled: Boolean = false
)
