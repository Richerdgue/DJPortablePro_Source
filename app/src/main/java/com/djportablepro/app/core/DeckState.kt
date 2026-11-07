package com.djportablepro.app.core

import com.djportablepro.app.midi.mapping.DjAction

/**
 * Represents the logical state of a DJ deck.
 */
data class DeckState(
    val deck: Deck,
    val isPlaying: Boolean = false,
    val isCueActive: Boolean = false,
    val isSyncActive: Boolean = false,
    val isPflActive: Boolean = false,
    val volume: Int = 0,
    val gain: Int = 64,
    val high: Int = 64,
    val mid: Int = 64,
    val low: Int = 64,
    val filter: Int = 64,
    val pitch: Int = 64,
    val bpm: Float = 128f,
    val beatPhase: Float = 0f,
    val downbeatPhase: Float = 0f,
    val firstBeatMs: Double = 0.0,
    val confidence: Float = 0f,
    val isVinylMode: Boolean = true,
    val isSlipMode: Boolean = false,
    val isQuantizeEnabled: Boolean = false,
    val isKeylockEnabled: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isPlatterTouched: Boolean = false,
    val isRingTouched: Boolean = false,
    val jogDelta: Int = 0,
    val performanceMode: DjAction.PerformanceMode.Mode = DjAction.PerformanceMode.Mode.HOT_CUE,
    val vuLevel: Int = 0
)
