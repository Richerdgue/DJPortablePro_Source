package com.djportablepro.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a track in the local DJ library.
 * Persists C++ NDK DSP analysis results (BPM, BeatGrid downbeat frame, waveform BLOB, confidence, duration)
 * to avoid expensive re-analysis of previously processed audio files.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val filePath: String,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val bpm: Double = 0.0,
    val firstBeatFrame: Long = 0L,
    val confidence: Double = 0.0,
    val beatDurationMs: Double = 0.0,
    val waveformBytes: ByteArray? = null,
    val isAnalyzed: Boolean = false,
    val lastAnalyzedTimestamp: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackEntity

        if (filePath != other.filePath) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (durationMs != other.durationMs) return false
        if (bpm != other.bpm) return false
        if (firstBeatFrame != other.firstBeatFrame) return false
        if (confidence != other.confidence) return false
        if (beatDurationMs != other.beatDurationMs) return false
        if (waveformBytes != null) {
            if (other.waveformBytes == null) return false
            if (!waveformBytes.contentEquals(other.waveformBytes)) return false
        } else if (other.waveformBytes != null) return false
        if (isAnalyzed != other.isAnalyzed) return false
        if (lastAnalyzedTimestamp != other.lastAnalyzedTimestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + bpm.hashCode()
        result = 31 * result + firstBeatFrame.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + beatDurationMs.hashCode()
        result = 31 * result + (waveformBytes?.contentHashCode() ?: 0)
        result = 31 * result + isAnalyzed.hashCode()
        result = 31 * result + lastAnalyzedTimestamp.hashCode()
        return result
    }
}
