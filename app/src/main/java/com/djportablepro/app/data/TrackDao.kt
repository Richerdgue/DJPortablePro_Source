package com.djportablepro.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for Room database operations on [TrackEntity].
 * Provides fast queries and targeted updates for C++ NDK DSP analysis caching.
 */
@Dao
interface TrackDao {

    /**
     * Fast lookup of a track by its absolute file path or URI.
     */
    @Query("SELECT * FROM tracks WHERE filePath = :filePath LIMIT 1")
    suspend fun getTrackByPath(filePath: String): TrackEntity?

    /**
     * Alias query for lookup by track ID / path.
     */
    @Query("SELECT * FROM tracks WHERE filePath = :filePath LIMIT 1")
    suspend fun getTrackById(filePath: String): TrackEntity?

    /**
     * Updates only the C++ NDK DSP analysis fields for a specific track.
     */
    @Query(
        """
        UPDATE tracks 
        SET bpm = :bpm, 
            firstBeatFrame = :firstBeatFrame, 
            confidence = :confidence, 
            beatDurationMs = :beatDurationMs, 
            isAnalyzed = :isAnalyzed, 
            lastAnalyzedTimestamp = :lastAnalyzedTimestamp 
        WHERE filePath = :filePath
        """,
    )
    suspend fun updateAnalysisResult(
        filePath: String,
        bpm: Double,
        firstBeatFrame: Long,
        confidence: Double,
        beatDurationMs: Double,
        isAnalyzed: Boolean,
        lastAnalyzedTimestamp: Long,
    ): Int

    /**
     * Inserts or replaces a track record in the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(track: TrackEntity)

    /**
     * Retrieves all tracks that have been analyzed by the native engine.
     */
    @Query("SELECT * FROM tracks WHERE isAnalyzed = 1 AND bpm > 0.0")
    suspend fun getAnalyzedTracks(): List<TrackEntity>

    /**
     * Retrieves all tracks stored in the library.
     */
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    suspend fun getAllTracks(): List<TrackEntity>
}
