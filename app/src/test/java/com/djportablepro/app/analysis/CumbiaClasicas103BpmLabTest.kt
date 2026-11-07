package com.djportablepro.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Real Audio DSP Laboratory Test for Cumbia Clásicas 103 BPM Tracks.
 * Verifies that 103 BPM tracks are correctly resolved to 103.0 BPM and not shifted by 9/8 (116.0 BPM).
 */
class CumbiaClasicas103BpmLabTest {

    private val bpmService = BpmAnalysisService()
    private val folderPath = "C:/Users/RICHIE/Music/Cumbia Clasicas EC RMX 2026/"

    @Test
    fun testCumbia103BpmExtractionAndUnfolding() {
        val file1Name = "103 - María De Los Angeles - Así Fue (Andres Beats Intro 2026).mp3"
        val file2Name = "103 - Silvana - A Las Puertas De El Cielo (Andres Beats Intro 2026).mp3"

        // 1. Verify Filename Hint Extraction
        val hint1 = bpmService.extractBpmHint(file1Name)
        val hint2 = bpmService.extractBpmHint(file2Name)

        assertEquals("Track 1 filename hint must resolve to 103.0 BPM", 103.0, hint1, 0.001)
        assertEquals("Track 2 filename hint must resolve to 103.0 BPM", 103.0, hint2, 0.001)

        // 2. Verify Polyrhythmic Un-folding (115.875 -> 103.0)
        val rawPolyrhythmicBpm = 115.875 // 103.0 * (9.0 / 8.0)
        val snappedBpm = bpmService.snapBpm(rawPolyrhythmicBpm)

        assertEquals("115.875 polyrhythmic 9/8 BPM must unfold back to 103.0 BPM", 103.0, snappedBpm, 0.001)

        // 3. Verify Tierra Canela 117.5 BPM -> 104.0 BPM Un-folding & SelectBestBpm Arbitration
        val tierraCanelaTrack = "104 - Tierra Canela - Joven Profesor De Amor (Andres Beats Intro 2026).mp3"
        val tierraCanelaResult = bpmService.selectBestBpm(tierraCanelaTrack, analyzedBpm = 117.5)
        assertEquals("117.5 BPM analyzed for 104.0 BPM track must resolve to 104.0 BPM", 104.0, tierraCanelaResult.bpm, 0.001)

        // 3. Optional Real File Folder Scan
        val folder = File(folderPath)
        if (folder.exists() && folder.isDirectory) {
            val mp3Files = folder.listFiles { _, name -> name.endsWith(".mp3", ignoreCase = true) } ?: emptyArray()
            println("=== CUMBIA CLASICAS 103 BPM REAL AUDIO LAB (${mp3Files.size} tracks) ===")
            for ((idx, file) in mp3Files.withIndex()) {
                val bestResult = bpmService.selectBestBpm(file.absolutePath, analyzedBpm = null)
                println(String.format(Locale.US, "[%02d] %-50s -> BPM: %.1f (%s)", idx + 1, file.name, bestResult.bpm, bestResult.source))
            }
        }
    }
}
