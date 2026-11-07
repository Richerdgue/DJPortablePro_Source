package com.djportablepro.app.analysis

import com.djportablepro.app.audio.DeckAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Senior DJ Audio DSP Unit Test Suite validating VirtualDJ / Cross DJ Style Beat Synchronization.
 * Validates:
 * 1. Tempo Matching & Base Pitch Ratio Calculation.
 * 2. Downbeat-Anchored Phase Error Calculation (eliminating intro silence offset).
 * 3. Master Pitch Tracking & Dynamic Rate Updating.
 * 4. Micro Pitch-Bend Nudge vs Instant Snap Jump thresholding.
 */
class FullSyncValidationTest {

    private val syncService = BeatSyncService()

    @Test
    fun `validateTempoMatchingAndPitchRatio`() {
        val masterBpm = 126.0
        val slaveOriginalBpm = 120.0

        val result = syncService.computeSyncState(
            masterBpm = masterBpm,
            masterPositionMs = 0.0,
            slaveOriginalBpm = slaveOriginalBpm,
            slavePositionMs = 0.0,
            syncState = BeatSyncService.SyncState.TEMPO_LOCK
        )

        assertEquals(126.0, result.targetBpm, 0.001)
        val expectedRatio = (126.0 / 120.0).toFloat() // 1.05f (+5% speedup)
        assertEquals(expectedRatio, result.pitchRatio, 0.001f)
    }

    @Test
    fun `validateDownbeatAnchoredPhaseErrorCalculation`() {
        // Master at 126 BPM (beat duration = 476.19ms)
        // Master track first kick drum is at 100ms
        // Slave track first kick drum is at 250ms
        // Master position = 1000ms -> Time from first kick = 900ms -> Phase = 900 % 476.19 = 423.81ms
        // Slave position = 1150ms -> Time from first kick = 900ms -> Phase = 900 % 476.19 = 423.81ms
        val phaseError = syncService.calculatePhaseErrorMs(
            masterPositionMs = 1000.0,
            masterBpm = 126.0,
            slavePositionMs = 1150.0,
            slaveBpm = 126.0,
            masterFirstBeatOffsetMs = 100.0,
            slaveFirstBeatOffsetMs = 250.0
        )

        // Since both kick times relative to their downbeat anchors match (900ms), phase error is 0.0ms!
        assertEquals(0.0, phaseError, 0.001)
    }

    @Test
    fun `validateMasterPitchTrackingState`() {
        val masterBaseBpm = 126.0f
        val masterPlaybackRate = 1.02f // Master pitch fader moved +2%
        val masterEffectiveBpm = (masterBaseBpm * masterPlaybackRate).toDouble() // 128.52 BPM

        val slaveBaseBpm = 120.0f

        val syncResult = syncService.computeSyncState(
            masterBpm = masterEffectiveBpm,
            masterPositionMs = 2000.0,
            slaveOriginalBpm = slaveBaseBpm.toDouble(),
            slavePositionMs = 2000.0,
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        val expectedSlaveRate = (masterEffectiveBpm / slaveBaseBpm).toFloat() // 1.071f
        assertEquals(expectedSlaveRate, syncResult.pitchRatio, 0.001f)
    }

    @Test
    fun `validatePitchBendCorrectionSlowdownWhenSlaveIsAhead`() {
        val beatDurationMs = 476.19 // 126 BPM
        val phaseErrorMs = 47.619 // Slave is 10% ahead of Master

        val correction = syncService.calculatePitchBendCorrection(
            phaseErrorMs = phaseErrorMs,
            beatDurationMs = beatDurationMs
        )

        // Slave is ahead -> correction must be negative (slowdown nudge)
        assertTrue("Correction must be negative to slow down slave deck", correction < 0.0f)
        assertEquals(-0.005f, correction, 0.001f) // -0.5% pitch bend
    }

    @Test
    fun `validateUniversalMultiGenreSyncSalsaVsEdm`() {
        val masterBpm = 128.0
        val masterFirstBeatMs = 180.0
        val masterPosMs = 15180.0

        val slaveOriginalBpm = 105.0
        val slaveFirstBeatMs = 45.0
        val slavePosMs = 12232.5 // Exactly 26 beats @ 128.0 BPM (26 * 468.75ms + 45.0ms)

        val phaseError = syncService.calculatePhaseErrorMs(
            masterPositionMs = masterPosMs,
            masterBpm = masterBpm,
            slavePositionMs = slavePosMs,
            slaveBpm = masterBpm,
            masterFirstBeatOffsetMs = masterFirstBeatMs,
            slaveFirstBeatOffsetMs = slaveFirstBeatMs,
            useBeatWrapping = true
        )

        val result = syncService.computeSyncState(
            masterBpm = masterBpm,
            masterPositionMs = masterPosMs,
            slaveOriginalBpm = slaveOriginalBpm,
            slavePositionMs = slavePosMs,
            masterFirstBeatFrame = (masterFirstBeatMs * 44.1).toLong(),
            slaveFirstBeatFrame = (slaveFirstBeatMs * 44.1).toLong(),
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        assertEquals(128.0, result.targetBpm, 0.001)

        val expectedRatio = (128.0 / 105.0).toFloat()
        assertEquals(expectedRatio, result.pitchRatio, 0.001f)

        assertEquals(0.0, phaseError, 0.001)
        assertTrue(result.isInSync)
    }
}
