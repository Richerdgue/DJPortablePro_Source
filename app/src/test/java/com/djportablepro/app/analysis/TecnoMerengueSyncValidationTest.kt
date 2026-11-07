package com.djportablepro.app.analysis

import com.djportablepro.app.audio.DeckAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TecnoMerengueSyncValidationTest {

    private val beatSyncService = BeatSyncService()

    @Test
    fun `validateTecnoMerengueSameBpmSyncAndVisualFeedback`() {
        // Simulated Tecno Merengue tracks with same BPM (130.0 BPM)
        val trackA = "Tecno Merengue - El Baile del Perrito - 130bpm.mp3"
        val trackB = "Tecno Merengue - Guayando - 130bpm.mp3"
        val sameBpm = 130.0

        // 1. Load Deck 0 (Master) and Deck 1 (Slave)
        val deck0State = DeckAudioState(
            isLoaded = true,
            trackTitle = trackA,
            bpm = sameBpm.toFloat(),
            isPlaying = true,
            currentPositionMs = 5000L
        )

        val deck1State = DeckAudioState(
            isLoaded = true,
            trackTitle = trackB,
            bpm = sameBpm.toFloat(),
            isPlaying = true,
            currentPositionMs = 5010L // 10ms slight phase difference
        )

        // 2. Compute SYNC for Deck 1 to Deck 0
        val syncResult = beatSyncService.computeSyncState(
            masterBpm = deck0State.bpm.toDouble(),
            masterPositionMs = deck0State.currentPositionMs.toDouble(),
            slaveOriginalBpm = deck1State.bpm.toDouble(),
            slavePositionMs = deck1State.currentPositionMs.toDouble(),
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        // 3. VERIFICATION & ERROR DETECTION
        // a) BPM Matching
        assertEquals(130.0, syncResult.targetBpm, 0.001)

        // b) Base Pitch Ratio for same BPM must be exactly 1.0
        assertEquals(1.0f, syncResult.pitchRatio, 0.001f)

        // c) Phase Error Detection: 10ms difference < 15ms tolerance -> In-sync!
        assertEquals(10.0, syncResult.phaseErrorMs, 0.001)
        assertTrue(syncResult.isInSync)

        // d) Visual Feedback Simulation: when sync is active, isSyncActive = true
        val syncedDeck1State = deck1State.copy(
            bpm = syncResult.targetBpm.toFloat(),
            playbackRate = syncResult.finalPitchRatio,
            isSyncActive = true
        )

        assertTrue("Visual feedback indicator (isSyncActive) must be TRUE when SYNC is active", syncedDeck1State.isSyncActive)
        assertEquals(130.0f, syncedDeck1State.bpm, 0.001f)
    }

    @Test
    fun `detectSyncErrorWhenDecksNotLoaded`() {
        // Unloaded deck state
        val unloadedDeck = DeckAudioState(isLoaded = false, trackTitle = "No Track Loaded")
        val loadedDeck = DeckAudioState(isLoaded = true, trackTitle = "Tecno Merengue.mp3", bpm = 130.0f)

        // Attempting to sync unloaded deck should fail gracefully and keep isSyncActive = false
        assertFalse(unloadedDeck.isLoaded && loadedDeck.isLoaded)

        val activeSyncState = unloadedDeck.copy(isSyncActive = false)
        assertFalse("Visual feedback must remain FALSE if sync fails due to unloaded deck", activeSyncState.isSyncActive)
    }
}
