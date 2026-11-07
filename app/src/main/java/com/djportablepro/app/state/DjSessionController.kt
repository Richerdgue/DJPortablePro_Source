package com.djportablepro.app.state

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.djportablepro.app.analysis.DeckAnalysisPipeline
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import com.djportablepro.app.core.Deck
import com.djportablepro.app.library.TrackAnalysisManager
import com.djportablepro.app.performance.DevicePerformanceProfileEvaluator
import com.djportablepro.app.midi.decoder.MidiProcessor
import com.djportablepro.app.midi.feedback.HerculesInpulse500FeedbackProfile
import com.djportablepro.app.midi.feedback.MidiFeedbackManager
import com.djportablepro.app.midi.mapping.ControllerProfile
import com.djportablepro.app.midi.mapping.ControllerProfileRegistry
import com.djportablepro.app.midi.mapping.DeviceDescriptor
import com.djportablepro.app.midi.mapping.GenericMidiProfile
import com.djportablepro.app.midi.mapping.HerculesImpulse500Profile
import com.djportablepro.app.midi.usb.UsbMidiManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates the live DJ runtime: audio, MIDI, state, and feedback.
 * Refactorizado para ser independiente de la vista (Pure Compose).
 */
class DjSessionController(
    private val activity: AppCompatActivity,
) {
    private val performanceProfile = DevicePerformanceProfileEvaluator.evaluate(activity)
    private val eventBus = DjEventBus()
    private val midiProcessor = MidiProcessor(eventBus)
    private val audioPlayer = NativeAudioPlayer(activity)
    private val trackAnalysisManager = TrackAnalysisManager(activity, audioPlayer)
    private val deckAnalysisPipeline = DeckAnalysisPipeline()
    private val stateManager = DjStateManager(eventBus, audioPlayer, activity.lifecycleScope)
    private val tempoController = DeckTempoController(audioPlayer, stateManager, deckAnalysisPipeline)
    private val usbMidiManager = UsbMidiManager(activity, midiProcessor)
    private val midiFeedbackManager = MidiFeedbackManager(
        stateManager,
        usbMidiManager,
        HerculesInpulse500FeedbackProfile(),
        activity.lifecycleScope,
    )

    init {
        midiProcessor.activeProfile = HerculesImpulse500Profile()
        audioPlayer.initialize(performanceProfile.audioEngineTier)
        usbMidiManager.startDiscovery()
        startBeatPhaseLoop()
    }

    fun onResume() {
        // No se requiere acción de vista aquí en Compose puro
    }
    
    fun onPause() {
        // No se requiere acción de vista aquí en Compose puro
    }

    fun loadTrackToDeck(deckIndex: Int, path: String) {
        val loaded = audioPlayer.load(deckIndex, path)
        if (!loaded) return

        val fileName = path.substringAfterLast('/')
        val title = fileName.substringBeforeLast('.')

        activity.lifecycleScope.launch {
            val analysis = trackAnalysisManager.getOrAnalyze(path)
            if (analysis == null) return@launch

            tempoController.applyTrackAnalysis(
                deckIndex = deckIndex,
                trackName = title,
                analyzedBpm = analysis.bpm.toDouble()
            )
        }
    }

    fun currentControllerProfileName(): String = midiProcessor.activeProfile?.name ?: "Sin perfil activo"
    fun currentPerformanceProfile(): String = performanceProfile.modeLabel

    fun setControllerProfile(profile: ControllerProfile) {
        midiProcessor.loadProfile(profile)
    }

    fun selectAutoControllerProfile(device: DeviceDescriptor? = null) {
        if (device != null) {
            midiProcessor.selectProfileForDevice(device)
            return
        }
        midiProcessor.loadProfile(ControllerProfileRegistry.defaultProfile())
    }

    fun loadControllerProfileFromJson(json: String): ControllerProfile {
        return midiProcessor.importProfileFromJson(json)
    }

    private fun startBeatPhaseLoop() {
        activity.lifecycleScope.launch {
            while (isActive) {
                val deckABpm = stateManager.deckAState.value.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 128.0
                val deckBBpm = stateManager.deckBState.value.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 128.0
                tempoController.updateRuntimePhase(0, deckABpm, "deck-a")
                tempoController.updateRuntimePhase(1, deckBBpm, "deck-b")
                delay(50L)
            }
        }
    }

    fun release() {
        usbMidiManager.release()
        audioPlayer.release()
    }
    
    // API para interactuar con la lógica de tempo desde la UI
    fun syncDeck(deckIndex: Int) = tempoController.syncDeckToReference(deckIndex)
}
