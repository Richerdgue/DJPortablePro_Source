package com.djportablepro.app.analysis

import com.djportablepro.app.audio.DeckAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Live Audio Analysis & Beat Matching Verification Test Suite
 * for tracks in "Los Excelentes )))" folder.
 */
class LosExcelentesLiveAudioAnalysisTest {

    private val bpmService = BpmAnalysisService()
    private val beatSyncService = BeatSyncService()

    private val folderPath = "C:/Users/RICHIE/Downloads/DJPortablePro/DJPortablePro/app/src/main/java/com/djportablepro/app/analysis/Los Excelentes )))/"

    @Test
    fun testLosExcelentesFolderTracksBpmExtraction() {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return // Skip if folder is not present in this environment
        }

        val mp3Files = folder.listFiles { _, name -> name.endsWith(".mp3", ignoreCase = true) }
        assertNotNull("Folder must contain MP3 files", mp3Files)
        assertTrue("Folder must contain at least 5 MP3 files", mp3Files!!.size >= 5)

        println("=== AUDITING 'Los Excelentes )))' LIVE AUDIO FILES (${mp3Files.size} tracks) ===")

        for (file in mp3Files) {
            val fileName = file.name
            val fullPath = file.absolutePath

            // Extract BPM hint using BpmAnalysisService
            val detectedBpm = bpmService.extractBpmHint(fullPath)

            println(String.format(Locale.US, "Track: %s -> Detected BPM Hint: %.1f", fileName, detectedBpm))

            // Every track in Los Excelentes ))) folder is 126.0 BPM
            assertEquals("BPM for '$fileName' must resolve to 126.0 BPM", 126.0, detectedBpm, 0.001)
        }
    }

    @Test
    fun testLosExcelentesBeatSyncAndPhaseLock() {
        // Test Sync between Track 1 ("Amor a Dios Edit") and Track 2 ("Conflicto Edit")
        val track1Name = "Amor a Dios   Edit - Dj Angelvis Diaz.mp3"
        val track2Name = "Conflicto   Edit - Dj Angelvis Diaz.mp3"
        val trackBpm = 126.0

        // Master Deck 0 playing at 126.0 BPM
        val masterDeck = DeckAudioState(
            isLoaded = true,
            trackTitle = track1Name,
            bpm = trackBpm.toFloat(),
            isPlaying = true,
            currentPositionMs = 12450L,
            firstBeatMs = 120.0 // First kick at 120ms
        )

        // Slave Deck 1 playing at 126.0 BPM with slight phase offset
        val slaveDeck = DeckAudioState(
            isLoaded = true,
            trackTitle = track2Name,
            bpm = trackBpm.toFloat(),
            isPlaying = true,
            currentPositionMs = 12465L, // 15ms phase offset before sync
            firstBeatMs = 135.0 // First kick at 135ms
        )

        // 1. Calculate phase error relative to first beat anchors
        val phaseError = beatSyncService.calculatePhaseErrorMs(
            masterPositionMs = masterDeck.currentPositionMs.toDouble(),
            masterBpm = masterDeck.bpm.toDouble(),
            slavePositionMs = slaveDeck.currentPositionMs.toDouble(),
            slaveBpm = slaveDeck.bpm.toDouble(),
            masterFirstBeatOffsetMs = masterDeck.firstBeatMs,
            slaveFirstBeatOffsetMs = slaveDeck.firstBeatMs
        )

        println("Master Kick Time = ${masterDeck.currentPositionMs - masterDeck.firstBeatMs} ms")
        println("Slave Kick Time = ${slaveDeck.currentPositionMs - slaveDeck.firstBeatMs} ms")
        println("Calculated Phase Error = $phaseError ms")

        // 2. Compute SYNC state
        val syncResult = beatSyncService.computeSyncState(
            masterBpm = masterDeck.bpm.toDouble(),
            masterPositionMs = masterDeck.currentPositionMs.toDouble(),
            slaveOriginalBpm = slaveDeck.bpm.toDouble(),
            slavePositionMs = slaveDeck.currentPositionMs.toDouble(),
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        // 3. VERIFICATIONS
        // Target BPM must be 126.0
        assertEquals(126.0, syncResult.targetBpm, 0.001)

        // Base Pitch Ratio for same BPM must be 1.0f
        assertEquals(1.0f, syncResult.pitchRatio, 0.001f)

        // Phase error relative to physical kick anchors must be 0.0ms (12330ms vs 12330ms)
        assertEquals(0.0, phaseError, 0.001)

        // Visual feedback state
        val syncedSlaveDeck = slaveDeck.copy(
            bpm = syncResult.targetBpm.toFloat(),
            playbackRate = syncResult.finalPitchRatio,
            isSyncActive = true
        )

        assertTrue("Visual feedback indicator (isSyncActive) must be TRUE", syncedSlaveDeck.isSyncActive)
        assertEquals(126.0f, syncedSlaveDeck.bpm, 0.001f)

        println("=== 'Los Excelentes )))' LIVE SYNC TEST COMPLETED SUCCESSFULLY ===")
    }

    @Test
    fun testLosExcelentesLarge410msPhaseDriftCorrection() {
        val trackBpm = 126.0

        val masterDeck = DeckAudioState(
            isLoaded = true,
            trackTitle = "Amor a Dios Edit.mp3",
            bpm = trackBpm.toFloat(),
            isPlaying = true,
            currentPositionMs = 12450L,
            firstBeatMs = 0.0
        )

        val slaveDeck = DeckAudioState(
            isLoaded = true,
            trackTitle = "Conflicto Edit.mp3",
            bpm = trackBpm.toFloat(),
            isPlaying = true,
            currentPositionMs = 12860L,
            firstBeatMs = 0.0
        )

        val syncResult = beatSyncService.computeSyncState(
            masterBpm = masterDeck.bpm.toDouble(),
            masterPositionMs = masterDeck.currentPositionMs.toDouble(),
            slaveOriginalBpm = slaveDeck.bpm.toDouble(),
            slavePositionMs = slaveDeck.currentPositionMs.toDouble(),
            syncState = BeatSyncService.SyncState.PHASE_LOCK
        )

        println("410.76ms Drift Test - Phase Error Ms: ${syncResult.phaseErrorMs}, Seek Frames: ${syncResult.seekFrameAdjustment}")

        assertTrue("Micro Phase error must be wrapped within 1-beat window (< 100ms)", abs(syncResult.phaseErrorMs) < 100.0)
        assertTrue("Seek frame adjustment must trigger to jump the 410ms beat offset", syncResult.seekFrameAdjustment > 0L)
    }
}
