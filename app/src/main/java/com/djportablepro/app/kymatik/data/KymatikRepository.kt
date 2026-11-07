package com.djportablepro.app.kymatik.data

import android.content.Context
import com.djportablepro.app.library.TrackDatabase
import com.djportablepro.app.kymatik.domain.KymatikResult

/**
 * Kymatik SQLite Database Persistence Layer.
 */
class KymatikRepository(context: Context) {
    private val db = TrackDatabase(context)

    fun getCachedAnalysis(filePath: String): KymatikResult? {
        val cached = db.getAnalysis(filePath) ?: return null
        return KymatikResult(
            bpm = cached.bpm.toDouble(),
            firstBeatFrame = cached.firstBeatFrame,
            confidence = 0.95,
            key = "8A",
            waveform = cached.waveform,
            version = cached.version
        )
    }

    fun saveAnalysis(filePath: String, result: KymatikResult) {
        db.saveAnalysis(
            hash = filePath,
            bpm = result.bpm.toFloat(),
            firstBeat = result.firstBeatFrame,
            waveform = result.waveform,
            version = result.version,
            altKey = filePath
        )
    }
}
