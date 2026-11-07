package com.djportablepro.app.analysis

import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.logging.DjLogger

enum class SyncMode {
    BEAT, // 1 beat (1/4 note)
    BAR   // 4 beats (1 full bar - Kick with Kick, Sub-Bass with Sub-Bass)
}

data class SyncResult(
    val slaveDeckIndex: Int,
    val masterDeckIndex: Int,
    val targetBpm: Double,
    val targetPlaybackRate: Float,
    val deltaFrames: Long,
    val phaseErrorMs: Double,
    val mode: SyncMode,
    val isSuccess: Boolean
)

/**
 * Senior DJ Audio DSP Beat & Bar Sync Engine.
 * Aligns Master and Slave decks for exact tempo match and 4-beat Bar / Phrase rhythm alignment
 * (Bass with Bass, Kick with Kick) with minimum circular frame distance projection.
 */
class BeatSyncEngine {

    companion object {
        private const val TAG = "BeatSyncEngine"
        private const val SAMPLE_RATE = 44100.0
    }

    /**
     * Triggers sync between Slave and Master deck.
     * Calculates exact pitch multiplier: targetPlaybackRate = (masterEffectiveBpm / slaveBaseBpm)
     * and projects frame correction using 4-beat Bar circular minimum distance.
     */
    fun triggerSync(
        slaveDeckIdx: Int,
        masterDeckIdx: Int,
        slaveFrame: Long,
        masterFrame: Long,
        masterPitch: Float = 1.0f,
        mode: SyncMode = SyncMode.BAR
    ): SyncResult {
        val slaveState = if (slaveDeckIdx == 0) DjPlayerController.deck1State.value else DjPlayerController.deck2State.value
        val masterState = if (masterDeckIdx == 0) DjPlayerController.deck1State.value else DjPlayerController.deck2State.value

        if (!slaveState.isLoaded || !masterState.isLoaded) {
            DjLogger.w(TAG, "[SYNC_FAIL] Both decks must have loaded tracks to trigger sync.")
            return SyncResult(slaveDeckIdx, masterDeckIdx, 128.0, 1.0f, 0L, 0.0, mode, false)
        }

        // 1. Calculate Master Effective BPM and Target Playback Rate
        val masterBaseBpm = masterState.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 128.0
        val slaveBaseBpm = slaveState.bpm.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 128.0

        val masterEffectiveBpm = masterBaseBpm * masterPitch.toDouble()
        val targetPlaybackRate = (masterEffectiveBpm / slaveBaseBpm).toFloat().coerceIn(0.5f, 2.0f)

        // 2. Compute frames per period (1 beat or 4 beats for BAR mode)
        val beatsPerPeriod = if (mode == SyncMode.BAR) 4.0 else 1.0
        val masterFramesPerPeriod = (SAMPLE_RATE * 60.0 / masterBaseBpm) * beatsPerPeriod
        val slaveFramesPerPeriod = (SAMPLE_RATE * 60.0 / slaveBaseBpm) * beatsPerPeriod

        // 3. Downbeat / First Beat Frame Anchors
        val masterFirstBeatFrame = (masterState.firstBeatMs * 44.1).toLong()
        val slaveFirstBeatFrame = (slaveState.firstBeatMs * 44.1).toLong()

        // 4. Calculate relative position in current Bar / Beat period
        val masterRelFrame = staticModulo(masterFrame - masterFirstBeatFrame, masterFramesPerPeriod)
        val masterPhaseRatio = masterRelFrame / masterFramesPerPeriod

        val slaveRelFrame = staticModulo(slaveFrame - slaveFirstBeatFrame, slaveFramesPerPeriod)
        val slavePhaseRatio = slaveRelFrame / slaveFramesPerPeriod

        // 5. Delta phase ratio calculation with Minimum Circular Distance wrapping [-0.5, +0.5]
        var barPhaseDiffRatio = slavePhaseRatio - masterPhaseRatio
        while (barPhaseDiffRatio > 0.5) barPhaseDiffRatio -= 1.0
        while (barPhaseDiffRatio < -0.5) barPhaseDiffRatio += 1.0

        val deltaFrames = (barPhaseDiffRatio * slaveFramesPerPeriod).toLong()
        val phaseErrorMs = (deltaFrames.toDouble() / SAMPLE_RATE) * 1000.0

        DjLogger.i(
            TAG,
            "[TRIGGER_SYNC] Mode=$mode | Slave Deck $slaveDeckIdx (BPM=$slaveBaseBpm) -> Master Deck $masterDeckIdx (EffectiveBPM=$masterEffectiveBpm) | Rate=$targetPlaybackRate | DeltaFrames=$deltaFrames (${phaseErrorMs}ms)"
        )

        return SyncResult(
            slaveDeckIndex = slaveDeckIdx,
            masterDeckIndex = masterDeckIdx,
            targetBpm = masterEffectiveBpm,
            targetPlaybackRate = targetPlaybackRate,
            deltaFrames = deltaFrames,
            phaseErrorMs = phaseErrorMs,
            mode = mode,
            isSuccess = true
        )
    }

    private fun staticModulo(value: Long, period: Double): Double {
        if (period <= 0.0) return 0.0
        val mod = (value.toDouble() % period)
        return if (mod < 0.0) mod + period else mod
    }
}
