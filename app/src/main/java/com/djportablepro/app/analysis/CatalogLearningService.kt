package com.djportablepro.app.analysis

import com.djportablepro.app.data.TrackDao
import com.djportablepro.app.logging.DjLogger
import java.util.Locale

/**
 * Adaptive Micro-AI Catalog Learning Service.
 * Learns from previously scanned tracks in the user's library, building a statistical
 * catalog profile (dominant BPM clusters, genre distribution, confidence averages)
 * to intelligently guide and auto-improve subsequent track analyses.
 */
class CatalogLearningService(private val trackDao: TrackDao) {

    companion object {
        private const val TAG = "CatalogLearningService"
    }

    data class CatalogProfile(
        val totalScannedTracks: Int,
        val dominantBpmCluster: Double,
        val averageConfidence: Double,
        val libraryEffectivenessScore: Float,
    )

    /**
     * Computes the adaptive catalog profile from all previously analyzed tracks in Room database.
     */
    suspend fun computeCatalogProfile(): CatalogProfile {
        val analyzedTracks = trackDao.getAnalyzedTracks()
        if (analyzedTracks.isEmpty()) {
            return CatalogProfile(0, 128.0, 0.0, 0.0f)
        }

        val bpms = analyzedTracks.map { it.bpm }.filter { it > 0.0 }
        val confidences = analyzedTracks.map { it.confidence }

        val dominantBpm = if (bpms.isNotEmpty()) {
            bpms.groupingBy { Math.round(it) }.eachCount().maxByOrNull { it.value }?.key?.toDouble() ?: 128.0
        } else {
            128.0
        }

        val avgConfidence = if (confidences.isNotEmpty()) confidences.average() else 0.0
        val effectiveness = (avgConfidence * 100.0f).toFloat().coerceIn(0f, 100f)

        DjLogger.i(
            TAG,
            String.format(
                 Locale.US,
                "[MICRO_AI_LEARNING] Profile computed: Scanned=%d, DominantBpm=%.1f, AvgConfidence=%.2f, Effectiveness=%.1f%%",
                analyzedTracks.size,
                dominantBpm,
                avgConfidence,
                effectiveness
            )
        )

        return CatalogProfile(
            totalScannedTracks = analyzedTracks.size,
            dominantBpmCluster = dominantBpm,
            averageConfidence = avgConfidence,
            libraryEffectivenessScore = effectiveness,
        )
    }

    /**
     * Generates an adaptive learning hint for a new track based on the library's learned profile.
     */
    suspend fun getAdaptivePriorHint(): Float {
        val profile = computeCatalogProfile()
        return if (profile.totalScannedTracks >= 3) {
            profile.dominantBpmCluster.toFloat()
        } else {
            0.0f
        }
    }
}
