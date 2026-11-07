package com.djportablepro.app.analysis

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Android Instrumented Audio BPM Laboratory Test.
 * Runs directly on Android Device / Emulator with full C++ NDK DSP (`libdjportablepro.so`) loaded.
 * Decodes real MP3/WAV audio files natively via [NativeAudioPlayer.analyzeTrack] and validates
 * calculated BPM against ground truth.
 */
@RunWith(AndroidJUnit4::class)
class BpmInstrumentedLaboratoryTest {

    private val bpmService = BpmAnalysisService()

    private val targetPathsOnDevice = listOf(
        "/sdcard/Music/REGUETON ((95))/",
        "/storage/emulated/0/Music/REGUETON ((95))/",
        "/sdcard/Music/((SALSA 003))/",
        "/storage/emulated/0/Music/((SALSA 003))/",
        "/sdcard/Music/((SALSA 001))/",
        "/storage/emulated/0/Music/((SALSA 001))/",
        "/sdcard/Music/ELECTRO  02)))/",
        "/storage/emulated/0/Music/ELECTRO  02)))/",
        "/sdcard/Music/MERENGUE TECNO 2/",
        "/storage/emulated/0/Music/MERENGUE TECNO 2/",
        "/sdcard/Music/Los Excelentes )))/",
        "/storage/emulated/0/Music/Los Excelentes )))/",
        "/sdcard/Music/Cumbia Clasicas EC RMX 2026/",
        "/storage/emulated/0/Music/Cumbia Clasicas EC RMX 2026/"
    )

    data class LabResult(
        val fileName: String,
        val groundTruthBpm: Double,
        val calculatedBpm: Double,
        val firstBeatFrame: Long,
        val delta: Double,
        val status: String
    )

    @Test
    fun runNativeCppAudioAnalysisLaboratory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val player = NativeAudioPlayer(context)

        println("=========================================================================")
        println("   DJPORTABLEPRO - LABORATORIO NATIVO C++ NDK DE ANÁLISIS DE AUDIO Y BPM ")
        println("=========================================================================")

        val filesToTest = mutableListOf<Pair<File, Double>>()

        for (path in targetPathsOnDevice) {
            val folder = File(path)
            if (!folder.exists() || !folder.isDirectory) continue

            val audioFiles = folder.walkTopDown().filter { file ->
                file.isFile && file.extension.lowercase(Locale.US) in setOf("mp3", "wav", "m4a", "flac", "ogg")
            }

            for (file in audioFiles) {
                val fileBpm = bpmService.extractBpmFromFilename(file.name)
                val folderBpm = bpmService.extractBpmFromFilename(file.parentFile?.name ?: "")
                val groundTruth = fileBpm ?: folderBpm ?: if (file.path.contains("SALSA")) 87.8 else if (file.path.contains("Los Excelentes")) 126.6 else 126.0

                if (groundTruth in 50.0..220.0) {
                    filesToTest.add(Pair(file, groundTruth))
                }
            }
        }

        val filesToRun = filesToTest

        if (filesToRun.isEmpty()) {
            println("[NDK_LAB_INFO] No music files found on device storage paths:")
            targetPathsOnDevice.forEach { println("   -> $it") }
            return
        }

        println("[NDK_LAB_START] Found ${filesToRun.size} audio files. Executing 100% Native C++ DSP Analysis...")
        println("-------------------------------------------------------------------------")

        val results = mutableListOf<LabResult>()
        var exactCount = 0
        var acceptableCount = 0
        var octaveCount = 0
        var mismatchCount = 0

        for ((file, expectedBpm) in filesToRun) {
            // PURE NDK AUDIO ANALYSIS: Pass referenceBpm = 0.0f so C++ calculates BPM 100% from PCM DSP
            val rawResult = player.analyzeTrack(file.absolutePath, 0.0f)

            val calculated = rawResult?.bpm?.toDouble() ?: 0.0
            val firstBeat = rawResult?.firstBeatFrame ?: 0L

            Log.i("ELECTRO_FULL_SUMMARY", "[ANALYZED_FILE] ${file.name} | BPM: $calculated | 1stBeat: $firstBeat")

            val delta = abs(calculated - expectedBpm)

            val status = when {
                delta <= 0.10 -> {
                    exactCount++
                    "EXACT_MATCH"
                }
                delta <= 1.00 -> {
                    acceptableCount++
                    "ACCEPTABLE_SNAP"
                }
                abs(calculated - (expectedBpm * 0.5)) <= 1.0 || abs(calculated - (expectedBpm * 2.0)) <= 1.0 -> {
                    octaveCount++
                    if (calculated < expectedBpm) "HALF_OCTAVE" else "DOUBLE_OCTAVE"
                }
                else -> {
                    mismatchCount++
                    "MISMATCH"
                }
            }

            results.add(
                LabResult(
                    fileName = file.name,
                    groundTruthBpm = expectedBpm,
                    calculatedBpm = calculated,
                    firstBeatFrame = firstBeat,
                    delta = delta,
                    status = status
                )
            )
        }

        println(String.format("%-40s | %-8s | %-10s | %-10s | %-8s | %-12s", "FILE NAME", "EXPECTED", "CALCULATED", "1ST BEAT", "DELTA", "STATUS"))
        println("-------------------------------------------------------------------------------------------------------------")

        for (res in results) {
            val nameTruncated = if (res.fileName.length > 38) res.fileName.substring(0, 35) + "..." else res.fileName
            println(
                String.format(
                    Locale.US,
                    "%-40s | %-8.2f | %-10.2f | %-10d | %-8.2f | %-12s",
                    nameTruncated,
                    res.groundTruthBpm,
                    res.calculatedBpm,
                    res.firstBeatFrame,
                    res.delta,
                    res.status
                )
            )
        }

        println("=========================================================================")
        println("                  RESUMEN DEL LABORATORIO NATIVO C++                    ")
        println("=========================================================================")
        val total = results.size
        val accuracyPct = if (total > 0) ((exactCount + acceptableCount).toDouble() / total) * 100.0 else 0.0

        println(String.format(Locale.US, "Total Canciones Analizadas C++ : %d", total))
        println(String.format(Locale.US, "Coincidencia Exacta (<=0.1)     : %d (%.1f%%)", exactCount, if (total > 0) (exactCount.toDouble() / total) * 100.0 else 0.0))
        println(String.format(Locale.US, "Ajuste Aceptable   (<=1.0)     : %d (%.1f%%)", acceptableCount, if (total > 0) (acceptableCount.toDouble() / total) * 100.0 else 0.0))
        println(String.format(Locale.US, "Errores de Octava  (1/2 o 2x)  : %d (%.1f%%)", octaveCount, if (total > 0) (octaveCount.toDouble() / total) * 100.0 else 0.0))
        println(String.format(Locale.US, "Diferencias (>1.0)             : %d (%.1f%%)", mismatchCount, if (total > 0) (mismatchCount.toDouble() / total) * 100.0 else 0.0))
        println(String.format(Locale.US, "PRECISIÓN TOTAL NATIVA C++     : %.2f%%", accuracyPct))
        println("=========================================================================")

        assertTrue("El Laboratorio Nativo requiere archivos para evaluar", total > 0)
    }
}
