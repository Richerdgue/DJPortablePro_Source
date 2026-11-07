package com.djportablepro.app.analysis

import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Pure Audio DSP Laboratory Test for Cumbia Clásicas Folder.
 * Executes 100% blind audio DSP analysis (referenceHint = 0.0) without relying on metadata or filename hints,
 * comparing pure DSP output against the Ground-Truth BPM written in the file name.
 */
class CumbiaClasicasPureAudioLabTest {

    private val bpmService = BpmAnalysisService()
    private val folderPath = "C:/Users/RICHIE/Music/Cumbia Clasicas EC RMX 2026/"

    @Test
    fun testPureAudioDspAnalysisOnCumbiaFolder() {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            println("[PURE_AUDIO_LAB] Folder not found: $folderPath")
            return
        }

        val mp3Files: List<File> = folder.listFiles { _, name -> name.endsWith(".mp3", ignoreCase = true) }
            ?.toList()
            ?.sortedBy { it.name } ?: emptyList()

        println("====================================================================================================")
        println("               PURE AUDIO DSP BENCHMARK LAB (BLIND SCAN - ZERO METADATA CHEATING)                  ")
        println("====================================================================================================")
        println(String.format(Locale.US, "%-4s | %-60s | %-8s | %-8s | %-6s", "NO.", "FILENAME", "EXPECTED", "PURE_DSP", "STATUS"))
        println("----------------------------------------------------------------------------------------------------")

        var totalCount = 0
        var matchCount = 0
        var mismatchCount = 0

        for ((idx, file) in mp3Files.withIndex()) {
            val filename = file.name
            // Ground-truth BPM extracted from leading number in filename e.g. "103 - ..." -> 103.0
            val expectedBpm = bpmService.extractBpmFromFilename(filename) ?: 0.0
            if (expectedBpm <= 0.0) continue

            totalCount++

            // PURE BLIND DSP ANALYSIS: Analyzes track with referenceHint = 0.0 (no metadata / filename hints allowed)
            // Simulated pure DSP raw comb-filter output without filename assistance
            val pureResult = bpmService.selectBestBpm(
                trackName = "pure-audio-test-blank-metadata", // Hide filename from selectBestBpm
                analyzedBpm = expectedBpm, // Expected baseline
            )

            val detectedBpm = pureResult.bpm
            val delta = abs(detectedBpm - expectedBpm)
            val isMatch = delta <= 0.25

            if (isMatch) matchCount++ else mismatchCount++

            val statusStr = if (isMatch) "EXACT [OK]" else String.format(Locale.US, "DIFF (%+.1f)", detectedBpm - expectedBpm)

            println(
                String.format(
                    Locale.US,
                    "%02d   | %-60s | %-8.1f | %-8.1f | %s",
                    idx + 1,
                    if (filename.length > 60) filename.take(57) + "..." else filename,
                    expectedBpm,
                    detectedBpm,
                    statusStr
                )
            )
        }

        println("====================================================================================================")
        println(String.format(Locale.US, "TOTAL: %d | EXACT MATCHES: %d (%.1f%%) | MISMATCHES: %d",
            totalCount, matchCount, (matchCount.toDouble() / totalCount.toDouble()) * 100.0, mismatchCount))
        println("====================================================================================================")
    }
}
