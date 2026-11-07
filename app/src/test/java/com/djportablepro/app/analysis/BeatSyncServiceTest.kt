package com.djportablepro.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatSyncServiceTest {

    private val service = BeatSyncService()

    @Test
    fun `computeSyncState equalizes BPM and computes pitch ratio`() {
        val masterBpm = 128.0
        val slaveOriginalBpm = 120.0

        val result = service.computeSyncState(
            masterBpm = masterBpm,
            masterPositionMs = 0.0,
            slaveOriginalBpm = slaveOriginalBpm,
            slavePositionMs = 0.0,
            syncState = BeatSyncService.SyncState.TEMPO_LOCK
        )

        assertEquals(128.0, result.targetBpm, 0.001)
        assertEquals(128.0f / 120.0f, result.pitchRatio, 0.001f)
        assertEquals(result.pitchRatio, result.finalPitchRatio, 0.001f)
    }

    @Test
    fun `calculatePhaseErrorMs calculates cyclic phase offset in milliseconds`() {
        // Master at 128 BPM (beat duration = 468.75ms), position at 100ms
        // Slave at 128 BPM, position at 120ms (Slave is 20ms AHEAD)
        val phaseError = service.calculatePhaseErrorMs(
            masterPositionMs = 100.0,
            masterBpm = 128.0,
            slavePositionMs = 120.0,
            slaveBpm = 128.0
        )

        assertEquals(20.0, phaseError, 0.001)
    }

    @Test
    fun `calculatePitchBendCorrection generates smooth slowdown when slave is ahead`() {
        val beatDurationMs = 468.75 // 128 BPM
        val phaseErrorMs = 46.875 // 10% of beat duration ahead

        val correction = service.calculatePitchBendCorrection(
            phaseErrorMs = phaseErrorMs,
            beatDurationMs = beatDurationMs
        )

        // Slave is ahead -> correction should be negative (slowdown)
        assertTrue(correction < 0.0f)
        assertEquals(-0.005f, correction, 0.001f) // -0.5% pitch bend
    }

    @Test
    fun `computeSyncState in PHASE_LOCK indicates inSync when error is under 15ms`() {
        val resultInSync = service.computeSyncState(
            masterBpm = 128.0,
            masterPositionMs = 1000.0,
            slaveOriginalBpm = 128.0,
            slavePositionMs = 1005.0, // 5ms error < 15ms tolerance
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        assertTrue(resultInSync.isInSync)
        assertEquals(5.0, resultInSync.phaseErrorMs, 0.001)

        val resultOutOfSync = service.computeSyncState(
            masterBpm = 128.0,
            masterPositionMs = 1000.0,
            slaveOriginalBpm = 128.0,
            slavePositionMs = 1050.0, // 50ms error > 15ms tolerance
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        assertFalse(resultOutOfSync.isInSync)
    }
}
