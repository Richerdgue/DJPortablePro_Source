package com.djportablepro.app.midi.mapping

import com.djportablepro.app.core.Deck
import com.djportablepro.app.core.JogSource

sealed class DjAction {
    // Transport
    data class PlayPause(val deck: Deck) : DjAction()
    data class Cue(val deck: Deck) : DjAction()
    data class Sync(val deck: Deck) : DjAction()
    data class Pfl(val deck: Deck, val active: Boolean) : DjAction()

    // Jog
    data class JogMove(val deck: Deck, val source: JogSource, val delta: Int) : DjAction()
    data class JogTouch(val deck: Deck, val source: JogSource, val touched: Boolean) : DjAction()

    // Mixer
    data class Pitch(val deck: Deck, val value: Int) : DjAction()
    data class Gain(val deck: Deck, val value: Int) : DjAction()
    data class High(val deck: Deck, val value: Int) : DjAction()
    data class Mid(val deck: Deck, val value: Int) : DjAction()
    data class Low(val deck: Deck, val value: Int) : DjAction()
    data class Filter(val deck: Deck, val value: Int) : DjAction()
    data class Volume(val deck: Deck, val value: Int) : DjAction()

    data class Crossfader(val value: Int) : DjAction()
    data class MasterVolume(val value: Int) : DjAction()

    // Controls
    data class Shift(val deck: Deck, val pressed: Boolean) : DjAction()
    data class Vinyl(val deck: Deck, val enabled: Boolean) : DjAction()
    data class Slip(val deck: Deck, val enabled: Boolean) : DjAction()
    data class Quantize(val deck: Deck, val enabled: Boolean) : DjAction()
    data class Keylock(val deck: Deck, val enabled: Boolean) : DjAction()

    // Loops
    data class LoopIn(val deck: Deck) : DjAction()
    data class LoopOut(val deck: Deck) : DjAction()
    data class AutoLoop(val deck: Deck, val size: Int) : DjAction()
    data class Reloop(val deck: Deck) : DjAction()

    // Performance
    data class HotCue(val deck: Deck, val index: Int, val pressed: Boolean) : DjAction()
    data class Pad(val deck: Deck, val index: Int, val value: Int) : DjAction()
    data class PerformanceMode(val deck: Deck, val mode: Mode) : DjAction() {
        enum class Mode { HOT_CUE, LOOP, SLICER, SAMPLER }
    }

    // Library
    data class Load(val deck: Deck) : DjAction()
    object Preview : DjAction()
    data class BrowseEncoder(val delta: Int) : DjAction()
    data class BrowseHorizontal(val delta: Int) : DjAction()
    object BrowsePush : DjAction()
    object AutoDjToggle : DjAction()

    // FX
    data class FxButton(val deck: Deck, val index: Int, val pressed: Boolean) : DjAction()

    // System / Info
    data class VuMeter(val deck: Deck?, val value: Int) : DjAction()
}
