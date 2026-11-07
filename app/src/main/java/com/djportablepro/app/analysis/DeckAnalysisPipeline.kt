package com.djportablepro.app.analysis

import com.djportablepro.app.audio.engine.NativeBpmAnalyzer
import com.djportablepro.app.library.AnalysisResult
import com.djportablepro.app.logging.DjLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Async Deck Analysis Pipeline connecting Kotlin services with C++ NDK DSP Native Motor.
 * Consumes [NativeBpmAnalyzer] asynchronously on [Dispatchers.Default] via JNI calls
 * to receive [AnalysisResult] containing high-precision decimal BPM and firstBeatFrame downbeat anchor.
 */
class DeckAnalysisPipeline(
    private val nativeBpmAnalyzer: NativeBpmAnalyzer = NativeBpmAnalyzer(),
    private val bpmAnalysisService: BpmAnalysisService = BpmAnalysisService(),
    private val beatGridAnalysisService: BeatGridAnalysisService = BeatGridAnalysisService(),
    private val beatSyncService: BeatSyncService = BeatSyncService(),
) {

    companion object {
        private const val TAG = "DeckAnalysisPipeline"
    }

    data class DeckAnalysisResult(
        val bpm: Double,
        val source: String,
        val confidence: Double,
        val phase: Double,
        val downbeatPhase: Double,
        val beatDurationMs: Double,
        val firstBeatFrame: Long = 0L,
        val waveform: ByteArray = ByteArray(0),
        val isValid: Boolean,
    )

    /**
     * Asynchronously analyzes PCM audio float buffer through native C++ NDK engine.
     * Invokes JNI method via [NativeBpmAnalyzer], receiving [AnalysisResult] with decimal BPM and firstBeatFrame.
     */
    suspend fun analyzePcmBuffer(
        pcmData: FloatArray,
        sampleRate: Int,
        channels: Int,
        trackName: String = "",
        referenceHint: Float = 0.0f,
    ): DeckAnalysisResult = withContext(Dispatchers.Default) {
        if (pcmData.isEmpty() || sampleRate <= 0 || channels <= 0) {
            return@withContext createInvalidResult()
        }

        // Soft reference hint from track name if hint not explicitly supplied
        val softHint = if (referenceHint > 0f) referenceHint else bpmAnalysisService.extractBpmHint(trackName).toFloat()

        DjLogger.i(TAG, "[PCM_ANALYSIS] Calling native C++ engine for $trackName (${pcmData.size} samples, hint: $softHint)")

        // Execute JNI call to C++ BpmAnalyzer on Dispatchers.Default
        val nativeResult: AnalysisResult? = nativeBpmAnalyzer.analyzeBufferAsync(
            pcmData = pcmData,
            sampleRate = sampleRate,
            channels = channels,
            referenceHint = softHint,
        )

        processNativeResult(nativeResult, trackName, sampleRate)
    }

    /**
     * Asynchronously analyzes an audio file directly through native C++ NDK decoder & DSP engine.
     */
    suspend fun analyzeFile(
        path: String,
        trackName: String = "",
        referenceHint: Float = 0.0f,
    ): DeckAnalysisResult = withContext(Dispatchers.Default) {
        if (path.isBlank()) return@withContext createInvalidResult()

        val softHint = if (referenceHint > 0f) referenceHint else bpmAnalysisService.extractBpmHint(trackName).toFloat()

        DjLogger.i(TAG, "[FILE_ANALYSIS] Calling native C++ engine for $path (hint: $softHint)")

        val nativeResult: AnalysisResult? = nativeBpmAnalyzer.analyzeFileAsync(path, softHint)

        processNativeResult(nativeResult, trackName, 44100)
    }

    /**
     * Synchronous deck state analysis wrapper for runtime state updates and tempo controller integration.
     * Uses 100% priority for real C++ [analyzedBpm].
     */
    fun analyzeDeck(
        trackName: String,
        analyzedBpm: Double? = null,
        fallbackBpm: Double? = null,
        currentBeatPhase: Double = 0.0,
        downbeatBias: Double = 0.0,
    ): DeckAnalysisResult {
        val bpmResult = bpmAnalysisService.selectBestBpm(
            trackName = trackName,
            analyzedBpm = analyzedBpm,
            fallbackBpm = fallbackBpm,
        )

        if (!bpmAnalysisService.isValidBpm(bpmResult.bpm)) {
            return createInvalidResult()
        }

        val beatDurationMs = 60000.0 / bpmResult.bpm
        val firstBeatOffsetMs = (currentBeatPhase * beatDurationMs).coerceIn(0.0, beatDurationMs)

        val grid = beatGridAnalysisService.buildBeatGrid(
            bpm = bpmResult.bpm,
            firstBeatOffsetMs = firstBeatOffsetMs,
            downbeatHintBeats = downbeatBias,
            confidence = bpmResult.confidence,
        )

        val downbeatPhase = if (grid.isValid) {
            beatSyncService.estimateDownbeatPhase(grid.phase, downbeatBias)
        } else {
            0.0
        }

        return DeckAnalysisResult(
            bpm = grid.bpm,
            source = bpmResult.source,
            confidence = minOf(bpmResult.confidence, grid.confidence),
            phase = grid.phase,
            downbeatPhase = downbeatPhase,
            beatDurationMs = grid.beatDurationMs,
            isValid = grid.isValid,
        )
    }

    private fun processNativeResult(
        nativeResult: AnalysisResult?,
        trackName: String,
        sampleRate: Int,
    ): DeckAnalysisResult {
        if (nativeResult == null || nativeResult.bpm <= 0f) {
            return createInvalidResult()
        }

        val bpmResult = bpmAnalysisService.selectBestBpm(
            trackName = trackName,
            analyzedBpm = nativeResult.bpm.toDouble(),
        )

        if (!bpmAnalysisService.isValidBpm(bpmResult.bpm)) {
            return createInvalidResult()
        }

        val firstBeatOffsetMs = if (sampleRate > 0) {
            (nativeResult.firstBeatFrame * 1000.0) / sampleRate
        } else {
            0.0
        }

        val grid = beatGridAnalysisService.buildBeatGrid(
            bpm = bpmResult.bpm,
            firstBeatOffsetMs = firstBeatOffsetMs,
            confidence = bpmResult.confidence,
        )

        val downbeatPhase = if (grid.isValid) {
            beatSyncService.estimateDownbeatPhase(grid.phase, 0.0)
        } else {
            0.0
        }

        return DeckAnalysisResult(
            bpm = grid.bpm,
            source = bpmResult.source,
            confidence = grid.confidence,
            phase = grid.phase,
            downbeatPhase = downbeatPhase,
            beatDurationMs = grid.beatDurationMs,
            firstBeatFrame = nativeResult.firstBeatFrame,
            isValid = grid.isValid,
        )
    }

    private fun createInvalidResult() = DeckAnalysisResult(
        bpm = 0.0,
        source = "invalid",
        confidence = 0.0,
        phase = 0.0,
        downbeatPhase = 0.0,
        beatDurationMs = 0.0,
        firstBeatFrame = 0L,
        isValid = false,
    )
}
