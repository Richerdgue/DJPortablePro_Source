package com.djportablepro.app.data

import com.djportablepro.app.analysis.DeckAnalysisPipeline
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRepositoryTest {

    private class FakeTrackDao : TrackDao {
        val tracks = mutableMapOf<String, TrackEntity>()

        override suspend fun getTrackByPath(filePath: String): TrackEntity? {
            return tracks[filePath]
        }

        override suspend fun getTrackById(filePath: String): TrackEntity? {
            return tracks[filePath]
        }

        override suspend fun updateAnalysisResult(
            filePath: String,
            bpm: Double,
            firstBeatFrame: Long,
            confidence: Double,
            beatDurationMs: Double,
            isAnalyzed: Boolean,
            lastAnalyzedTimestamp: Long
        ): Int {
            val existing = tracks[filePath] ?: return 0
            tracks[filePath] = existing.copy(
                bpm = bpm,
                firstBeatFrame = firstBeatFrame,
                confidence = confidence,
                beatDurationMs = beatDurationMs,
                isAnalyzed = isAnalyzed,
                lastAnalyzedTimestamp = lastAnalyzedTimestamp
            )
            return 1
        }

        override suspend fun insertOrReplace(track: TrackEntity) {
            tracks[track.filePath] = track
        }

        override suspend fun getAnalyzedTracks(): List<TrackEntity> {
            return tracks.values.filter { it.isAnalyzed && it.bpm > 0.0 }
        }

        override suspend fun getAllTracks(): List<TrackEntity> {
            return tracks.values.toList()
        }
    }

    @Test
    fun `getOrAnalyzeTrack returns cached Room result with zero NDK cost when already analyzed`() = runTest {
        val dao = FakeTrackDao()
        val path = "/sdcard/Music/House_128bpm.mp3"

        // Pre-populate Room DB with analyzed track
        dao.insertOrReplace(
            TrackEntity(
                filePath = path,
                title = "House Track",
                artist = "DJ Pro",
                bpm = 128.0,
                firstBeatFrame = 44100L,
                confidence = 0.95,
                beatDurationMs = 468.75,
                isAnalyzed = true,
                lastAnalyzedTimestamp = System.currentTimeMillis()
            )
        )

        val repository = TrackRepository(trackDao = dao)

        val result = repository.getOrAnalyzeTrack(filePath = path, title = "House Track")

        assertTrue(result.isValid)
        assertEquals(128.0, result.bpm, 0.001)
        assertEquals("database_cache", result.source)
        assertEquals(44100L, result.firstBeatFrame)
        assertEquals(468.75, result.beatDurationMs, 0.001)
    }

    @Test
    fun `savePcmAnalysisResult correctly saves analysis into Room`() = runTest {
        val dao = FakeTrackDao()
        val repository = TrackRepository(trackDao = dao)
        val path = "/sdcard/Music/Salsa_100bpm.wav"

        val pcmResult = DeckAnalysisPipeline.DeckAnalysisResult(
            bpm = 100.0,
            source = "analysis",
            confidence = 0.90,
            phase = 0.0,
            downbeatPhase = 0.0,
            beatDurationMs = 600.0,
            firstBeatFrame = 22050L,
            isValid = true
        )

        repository.savePcmAnalysisResult(
            filePath = path,
            result = pcmResult,
            title = "Salsa Track",
            artist = "Salsa King"
        )

        val saved = dao.getTrackByPath(path)
        assertTrue(saved != null && saved.isAnalyzed)
        assertEquals(100.0, saved?.bpm ?: 0.0, 0.001)
        assertEquals(22050L, saved?.firstBeatFrame ?: 0L)
    }
}
