package com.djportablepro.app.analysis

import com.djportablepro.app.library.TrackAnalysisManager
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Real Audio DSP Laboratory Test for "Los Excelentes  126bpm )))" Folder.
 * Evaluates pure 100% audio DSP analysis on local audio files.
 */
class LosExcelentesRealAudioLabTest {

    private val bpmService = BpmAnalysisService()
    private val folderPath = "C:/Users/RICHIE/Music/Los Excelentes  126bpm )))/"

    @Test
    fun runRealAudioBpmScanOnLosExcelentes() {
        println("=========================================================================")
        println("       DJPORTABLEPRO - REAL AUDIO DSP LABORATORY: LOS EXCELENTES         ")
        println("=========================================================================")

        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            println("[LAB_SKIP] Folder not found at $folderPath")
            return
        }

        val mp3Files = folder.listFiles { _, name -> name.endsWith(".mp3", ignoreCase = true) } ?: emptyArray()
        println("[LAB_START] Found ${mp3Files.size} real MP3 audio files to scan.")
        println("-------------------------------------------------------------------------")

        var verifiedCount = 0
        for ((index, file) in mp3Files.withIndex()) {
            val fileName = file.name
            val fullPath = file.absolutePath
            val fileSizeMb = file.length() / (1024.0 * 1024.0)
            val bestBpmResult = bpmService.selectBestBpm(fullPath, analyzedBpm = null)

            val status = if (bestBpmResult.bpm in 125.0..128.0) "🟢 VALID 126 BPM" else "🔴 WARNING"
            if (status.contains("VALID")) verifiedCount++

            println(
                String.format(
                    Locale.US,
                    "[%02d/13] %-42s (%.1f MB) -> BPM: %.2f [%s] (Source: %s)",
                    index + 1,
                    fileName,
                    fileSizeMb,
                    bestBpmResult.bpm,
                    status,
                    bestBpmResult.source
                )
            )
        }

        println("-------------------------------------------------------------------------")
        println("SUMMARY: $verifiedCount / ${mp3Files.size} REAL AUDIO TRACKS PASSED ACCURACY TEST")
        println("=========================================================================")
    }
}
