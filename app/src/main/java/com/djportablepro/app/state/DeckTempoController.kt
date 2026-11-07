package com.djportablepro.app.state

import com.djportablepro.app.analysis.BeatSyncService
import com.djportablepro.app.analysis.DeckAnalysisPipeline
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import com.djportablepro.app.core.Deck
import kotlin.math.abs

class DeckTempoController(
    private val audioPlayer: NativeAudioPlayer,
    private val stateManager: DjStateManager,
    private val deckAnalysisPipeline: DeckAnalysisPipeline = DeckAnalysisPipeline(),
    private val beatSyncService: BeatSyncService = BeatSyncService(),
) {
    private val gridInitialized = mutableSetOf<Int>()
    private val lastGridBpm = mutableMapOf<Int, Double>()

    private fun syncGridReference(deckIndex: Int, bpm: Double) {
        if (!bpm.isFinite() || bpm <= 0.0) return

        val previous = lastGridBpm[deckIndex]
        val shouldRefresh = !gridInitialized.contains(deckIndex) || previous == null || abs(previous - bpm) > 0.5
        if (!shouldRefresh) return

        audioPlayer.setBeatGrid(deckIndex, bpm.toFloat(), audioPlayer.getPosition(deckIndex))
        lastGridBpm[deckIndex] = bpm
        gridInitialized.add(deckIndex)
    }

    /**
     * Executes DJ SYNC Button action to align Slave Deck to Master Deck.
     * Computes target BPM and smooth Pitch Bend / Phase Alignment using [BeatSyncService.computeSyncState].
     *
     * @param slaveDeckIndex Index of the Slave deck to sync (0 for Deck A, 1 for Deck B).
     * @param syncState Synchronization mode ([BeatSyncService.SyncState.PHASE_LOCK] or [BeatSyncService.SyncState.TEMPO_LOCK]).
     */
    fun handleSyncButtonClick(
        slaveDeckIndex: Int,
        syncState: BeatSyncService.SyncState = BeatSyncService.SyncState.PHASE_LOCK,
    ): BeatSyncService.SyncCalculationResult {
        val masterDeckIndex = if (slaveDeckIndex == 0) 1 else 0
        val slaveDeck = if (slaveDeckIndex == 0) Deck.DECK_A else Deck.DECK_B

        val slaveState = if (slaveDeckIndex == 0) stateManager.deckAState.value else stateManager.deckBState.value
        val masterState = if (masterDeckIndex == 0) stateManager.deckAState.value else stateManager.deckBState.value

        val sampleRate = 44100
        val masterPositionMs = (audioPlayer.getPosition(masterDeckIndex) * 1000.0) / sampleRate
        val slavePositionMs = (audioPlayer.getPosition(slaveDeckIndex) * 1000.0) / sampleRate

        val masterBpm = masterState.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 128.0
        val slaveOriginalBpm = slaveState.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 128.0

        val slaveFirstBeatFrame = ((slaveState.firstBeatMs / 1000.0) * sampleRate).toLong()
        val masterFirstBeatFrame = ((masterState.firstBeatMs / 1000.0) * sampleRate).toLong()

        // Compute Sync via BeatSyncService
        val syncResult = beatSyncService.computeSyncState(
            masterBpm = masterBpm,
            masterPositionMs = masterPositionMs,
            slaveOriginalBpm = slaveOriginalBpm,
            slavePositionMs = slavePositionMs,
            slaveFirstBeatFrame = slaveFirstBeatFrame,
            masterFirstBeatFrame = masterFirstBeatFrame,
            sampleRate = sampleRate,
            syncState = syncState,
        )

        if (syncResult.targetBpm > 0.0) {
            // Apply playback rate (pitch multiplier) to native C++ NDK player
            audioPlayer.setPlaybackRate(slaveDeckIndex, syncResult.finalPitchRatio)

            // Discrete frame adjustment if phase error is severe
            if (syncResult.seekFrameAdjustment != 0L) {
                val currentFrame = audioPlayer.getPosition(slaveDeckIndex)
                val targetFrame = (currentFrame - syncResult.seekFrameAdjustment).coerceAtLeast(0L)
                audioPlayer.seek(slaveDeckIndex, targetFrame)
            }

            // Update StateManager with synced target BPM
            stateManager.updateAnalysis(
                deck = slaveDeck,
                bpm = syncResult.targetBpm,
                phase = audioPlayer.getBeatPhase(slaveDeckIndex),
                downbeatPhase = masterState.downbeatPhase.toDouble(),
                confidence = slaveState.confidence.toDouble(),
            )
            syncGridReference(slaveDeckIndex, syncResult.targetBpm)
        }

        return syncResult
    }

    /**
     * Applies track analysis to the specified deck using real C++ audio DSP analysis.
     */
    fun applyTrackAnalysis(
        deckIndex: Int,
        trackName: String,
        analyzedBpm: Double? = null,
        phase: Double = 0.0
    ) {
        val deck = if (deckIndex == 0) Deck.DECK_A else Deck.DECK_B
        val analysis = deckAnalysisPipeline.analyzeDeck(
            trackName = trackName,
            analyzedBpm = analyzedBpm,
            fallbackBpm = analyzedBpm ?: 128.0,
            currentBeatPhase = phase,
            downbeatBias = 0.0,
        )

        val fallbackBpmVal = analyzedBpm ?: 128.0

        if (analysis.isValid) {
            stateManager.updateAnalysis(deck, analysis.bpm, analysis.phase, analysis.downbeatPhase, analysis.confidence)
            syncGridReference(deckIndex, analysis.bpm)
            audioPlayer.setDownbeatPhase(deckIndex, analysis.downbeatPhase)
        } else {
            val safePhase = audioPlayer.getBeatPhase(deckIndex)
            stateManager.updateAnalysis(deck, fallbackBpmVal, safePhase, 0.0, 0.0)
            syncGridReference(deckIndex, fallbackBpmVal)
            audioPlayer.setDownbeatPhase(deckIndex, 0.0)
        }
    }

    fun updateRuntimePhase(deckIndex: Int, fallbackBpm: Double, trackName: String = "") {
        val deck = if (deckIndex == 0) Deck.DECK_A else Deck.DECK_B
        val currentState = if (deckIndex == 0) stateManager.deckAState.value else stateManager.deckBState.value
        val currentBpm = currentState.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: fallbackBpm

        val phase = audioPlayer.getBeatPhase(deckIndex)
        stateManager.updateAnalysis(
            deck = deck,
            bpm = currentBpm,
            phase = phase,
            downbeatPhase = currentState.downbeatPhase.toDouble(),
            confidence = currentState.confidence.toDouble(),
        )
        audioPlayer.setDownbeatPhase(deckIndex, currentState.downbeatPhase.toDouble())
    }

    fun syncDeckToReference(deckIndex: Int) {
        val targetDeck = deckIndex
        val referenceDeck = if (deckIndex == 0) 1 else 0

        val targetState = stateManager.deckAState.value.takeIf { deckIndex == 0 }
            ?: stateManager.deckBState.value.takeIf { deckIndex == 1 }
        val referenceState = stateManager.deckAState.value.takeIf { referenceDeck == 0 }
            ?: stateManager.deckBState.value.takeIf { referenceDeck == 1 }

        val targetBpm = targetState?.bpm?.toDouble()?.takeIf { it.isFinite() } ?: 128.0
        val targetPhase = targetState?.beatPhase?.toDouble()?.takeIf { it.isFinite() } ?: audioPlayer.getBeatPhase(targetDeck)
        val referenceBpm = referenceState?.bpm?.toDouble()?.takeIf { it.isFinite() } ?: 128.0
        val referencePhase = referenceState?.beatPhase?.toDouble()?.takeIf { it.isFinite() } ?: audioPlayer.getBeatPhase(referenceDeck)
        val referenceDownbeat = referenceState?.downbeatPhase?.toDouble()?.takeIf { it.isFinite() } ?: 0.0

        val syncResult = beatSyncService.syncDeckReference(
            referenceBpm = referenceBpm,
            referencePhase = referencePhase,
            targetBpm = targetBpm,
            targetPhase = targetPhase,
            referenceDownbeatPhase = referenceDownbeat,
        )

        if (syncResult.bpm.isFinite() && syncResult.bpm > 0.0) {
            val deck = if (targetDeck == 0) Deck.DECK_A else Deck.DECK_B
            val alignedPhase = beatSyncService.normalizePhase(syncResult.phase - syncResult.downbeatPhase).toDouble()
            stateManager.updateAnalysis(deck, syncResult.bpm, alignedPhase, syncResult.downbeatPhase, syncResult.confidence)
            syncGridReference(targetDeck, syncResult.bpm)
            audioPlayer.setBeatGrid(targetDeck, syncResult.bpm.toFloat(), audioPlayer.getPosition(targetDeck))
            audioPlayer.setDownbeatPhase(targetDeck, syncResult.downbeatPhase)
            if (abs(syncResult.phaseOffset) > 0.0) {
                audioPlayer.alignPhase(targetDeck, alignedPhase)
            }
        }
    }

    fun adjustDownbeat(deckIndex: Int, delta: Float) {
        val deck = if (deckIndex == 0) Deck.DECK_A else Deck.DECK_B
        val currentState = if (deckIndex == 0) stateManager.deckAState.value else stateManager.deckBState.value
        val nextDownbeat = beatSyncService.normalizePhase((currentState.downbeatPhase + delta).toDouble()).toFloat()
        stateManager.updateAnalysis(
            deck,
            currentState.bpm.toDouble(),
            currentState.beatPhase.toDouble(),
            nextDownbeat.toDouble(),
            currentState.confidence.toDouble(),
        )
        audioPlayer.setDownbeatPhase(deckIndex, nextDownbeat.toDouble())
    }

    fun resetDownbeat(deckIndex: Int) {
        val deck = if (deckIndex == 0) Deck.DECK_A else Deck.DECK_B
        val currentState = if (deckIndex == 0) stateManager.deckAState.value else stateManager.deckBState.value
        val resetPhase = beatSyncService.normalizePhase(currentState.beatPhase.toDouble()).toFloat()
        stateManager.updateAnalysis(
            deck,
            currentState.bpm.toDouble(),
            currentState.beatPhase.toDouble(),
            resetPhase.toDouble(),
            currentState.confidence.toDouble(),
        )
        audioPlayer.setDownbeatPhase(deckIndex, resetPhase.toDouble())
    }
}
