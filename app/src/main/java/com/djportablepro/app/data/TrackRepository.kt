package com.djportablepro.app.data

import com.djportablepro.app.analysis.CatalogLearningService
import com.djportablepro.app.analysis.DeckAnalysisPipeline
import com.djportablepro.app.analysis.DeckAnalysisPipeline.DeckAnalysisResult
import com.djportablepro.app.logging.DjLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Senior Track Repository coordinating Room Database caching with C++ NDK [DeckAnalysisPipeline]
 * and the Adaptive Micro-AI [CatalogLearningService].
 * Guarantees zero-CPU re-analysis for previously processed tracks and auto-improves analysis
 * using statistical catalog priors learned from previously scanned library tracks.
 */
class TrackRepository(
    private val trackDao: TrackDao,
    private val pipeline: DeckAnalysisPipeline = DeckAnalysisPipeline(),
    private val catalogLearningService: CatalogLearningService = CatalogLearningService(trackDao),
) {

    companion object {
        private const val TAG = "TrackRepository"
    }

    /**
     * Obtains BPM and BeatGrid analysis for a track.
     * 1. Checks Room Database cache first. If [TrackEntity.isAnalyzed] is true and bpm > 0, returns immediately with 0 NDK cost.
     * 2. Queries Micro-AI [CatalogLearningService] for an adaptive catalog prior hint based on library history.
     * 3. Delegates to native C++ NDK [DeckAnalysisPipeline] with the adaptive hint.
     * 4. On valid native analysis, automatically saves/updates the result in Room for future requests.
     */
    suspend fun getOrAnalyzeTrack(
        filePath: String,
        title: String = "",
        artist: String = "",
        referenceHint: Float = 0.0f,
        forceReanalyze: Boolean = false,
    ): DeckAnalysisResult = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext createInvalidResult()

        // 1. FAST ROOM LOOKUP (Only if not forcing re-analysis)
        val cachedTrack = if (!forceReanalyze) trackDao.getTrackByPath(filePath) else null
        if (cachedTrack != null && (cachedTrack.isAnalyzed && cachedTrack.bpm > 0.0)) {
            DjLogger.i(TAG, "[ROOM_CACHE_HIT] Track $filePath found in Room. BPM: ${cachedTrack.bpm}")
            return@withContext DeckAnalysisResult(
                bpm = cachedTrack.bpm,
                source = "database_cache",
                confidence = cachedTrack.confidence,
                phase = 0.0,
                downbeatPhase = 0.0,
                beatDurationMs = cachedTrack.beatDurationMs,
                firstBeatFrame = cachedTrack.firstBeatFrame,
                isValid = true,
            )
        }

        // 2. MICRO-AI CATALOG LEARNING PRIOR & FILENAME HINT
        val combinedNameForHint = if (title.isNotBlank()) "$filePath $title" else filePath
        val adaptiveHint = if (referenceHint <= 0.0f) catalogLearningService.getAdaptivePriorHint() else referenceHint

        // 3. NDK NATIVE C++ ANALYSIS (If not cached)
        DjLogger.i(TAG, "[ROOM_CACHE_MISS] Executing native C++ analysis for $filePath with adaptive hint: $adaptiveHint")
        val analysisResult = pipeline.analyzeFile(
            path = filePath,
            trackName = combinedNameForHint,
            referenceHint = adaptiveHint,
        )

        // 4. PERSIST ANALYSIS TO ROOM DATABASE
        if (analysisResult.isValid && analysisResult.bpm > 0.0) {
            val now = System.currentTimeMillis()
            val existing = cachedTrack ?: TrackEntity(
                filePath = filePath,
                title = title,
                artist = artist,
            )

            val updatedTrack = existing.copy(
                title = title.ifBlank { existing.title },
                artist = artist.ifBlank { existing.artist },
                bpm = analysisResult.bpm,
                firstBeatFrame = analysisResult.firstBeatFrame,
                confidence = analysisResult.confidence,
                beatDurationMs = analysisResult.beatDurationMs,
                waveformBytes = analysisResult.waveform,
                isAnalyzed = true,
                lastAnalyzedTimestamp = now,
            )

            trackDao.insertOrReplace(updatedTrack)
            DjLogger.i(TAG, "[ROOM_PERSIST_SUCCESS] Saved analysis for $filePath to Room. BPM: ${analysisResult.bpm}")
        }

        analysisResult
    }

    /**
     * Persists PCM buffer analysis result directly into Room database.
     */
    suspend fun savePcmAnalysisResult(
        filePath: String,
        result: DeckAnalysisResult,
        title: String = "",
        artist: String = "",
    ) = withContext(Dispatchers.IO) {
        if (filePath.isBlank() || !result.isValid || result.bpm <= 0.0) return@withContext

        val now = System.currentTimeMillis()
        val cached = trackDao.getTrackByPath(filePath)
        val track = (cached ?: TrackEntity(filePath = filePath, title = title, artist = artist)).copy(
            bpm = result.bpm,
            firstBeatFrame = result.firstBeatFrame,
            confidence = result.confidence,
            beatDurationMs = result.beatDurationMs,
            isAnalyzed = true,
            lastAnalyzedTimestamp = now,
        )

        trackDao.insertOrReplace(track)
        DjLogger.i(TAG, "[ROOM_PERSIST_PCM] Saved PCM analysis for $filePath. BPM: {result.bpm}")
    }

    /**
     * Direct query to retrieve a track from Room database.
     */
    suspend fun getTrack(filePath: String): TrackEntity? = withContext(Dispatchers.IO) {
        trackDao.getTrackByPath(filePath)
    }

    /**
     * Retrieves all analyzed tracks in the library.
     */
    suspend fun getAnalyzedTracks(): List<TrackEntity> = withContext(Dispatchers.IO) {
        trackDao.getAnalyzedTracks()
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
