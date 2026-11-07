package com.djportablepro.app.analysis

/**
 * Pure Mathematical BeatGrid Calculation Service.
 *
 * Eliminates all Kotlin-based BPM estimation/heuristics in favor of direct
 * mathematical grid construction and phase alignment using real C++ NDK DSP output values.
 */
class BeatGridAnalysisService {

    data class BeatGridResult(
        val bpm: Double,
        val beatDurationMs: Double,
        val firstBeatOffsetMs: Double,
        val phase: Double,
        val downbeatOffsetBeats: Double,
        val confidence: Double,
        val isValid: Boolean,
    )

    /**
     * Builds a BeatGrid using exact C++ DSP analyzed BPM and firstBeatOffsetMs.
     * Computes exact beat duration, phase [0.0, 1.0], and downbeat offsets.
     */
    fun buildBeatGrid(
        bpm: Double,
        firstBeatOffsetMs: Double = 0.0,
        downbeatHintBeats: Double = 0.0,
        confidence: Double = 0.9,
    ): BeatGridResult {
        if (!isValidBpm(bpm)) {
            return BeatGridResult(
                bpm = 0.0,
                beatDurationMs = 0.0,
                firstBeatOffsetMs = 0.0,
                phase = 0.0,
                downbeatOffsetBeats = 0.0,
                confidence = 0.0,
                isValid = false,
            )
        }

        val beatDurationMs = 60000.0 / bpm
        val normalizedOffset = normalizeOffset(firstBeatOffsetMs, beatDurationMs)
        val phase = (normalizedOffset / beatDurationMs).coerceIn(0.0, 1.0)
        val downbeat = normalizeDownbeat(downbeatHintBeats)
        val finalConfidence = estimateConfidence(bpm, confidence, phase)

        return BeatGridResult(
            bpm = bpm,
            beatDurationMs = beatDurationMs,
            firstBeatOffsetMs = normalizedOffset,
            phase = phase,
            downbeatOffsetBeats = downbeat,
            confidence = finalConfidence,
            isValid = true,
        )
    }

    /**
     * Pure mathematical calculation of current beat phase [0.0, 1.0] for a given position in ms.
     */
    fun estimateBeatPhase(beatMs: Double, bpm: Double): Double {
        if (!isValidBpm(bpm) || beatMs < 0.0) return 0.0
        val beatDurationMs = 60000.0 / bpm
        return ((beatMs % beatDurationMs) / beatDurationMs).coerceIn(0.0, 1.0)
    }

    /**
     * Validates if BPM is within valid DJ range.
     */
    fun isValidBpm(bpm: Double): Boolean = bpm.isFinite() && bpm in 50.0..250.0

    private fun normalizeOffset(offsetMs: Double, beatDurationMs: Double): Double {
        if (beatDurationMs <= 0.0) return 0.0
        val wrapped = offsetMs % beatDurationMs
        return if (wrapped < 0.0) wrapped + beatDurationMs else wrapped
    }

    private fun normalizeDownbeat(value: Double): Double = value.coerceIn(0.0, 7.0)

    private fun estimateConfidence(bpm: Double, confidence: Double, phase: Double): Double {
        val bpmScore = if (bpm in 70.0..180.0) 1.0 else 0.75
        val phaseScore = if (phase in 0.0..1.0) 1.0 else 0.5
        val inputScore = confidence.coerceIn(0.0, 1.0)
        return ((bpmScore * 0.45) + (phaseScore * 0.15) + (inputScore * 0.4)).coerceIn(0.0, 1.0)
    }
}
