package com.djportablepro.app.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TrackDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "dj_portable_pro.db"
        private const val DATABASE_VERSION = 5 // Version 5: Polyrhythmic Ratio 7/9 Un-folding (98.34 -> 126.5 BPM)

        const val TABLE_ANALYSIS = "analysis_cache"
        const val COL_HASH = "file_hash"
        const val COL_BPM = "bpm"
        const val COL_FIRST_BEAT = "first_beat_frame"
        const val COL_WAVEFORM = "waveform_data"
        const val COL_VERSION = "analyzer_version"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ANALYSIS (
                $COL_HASH TEXT PRIMARY KEY,
                $COL_BPM REAL NOT NULL,
                $COL_FIRST_BEAT INTEGER NOT NULL,
                $COL_WAVEFORM BLOB NOT NULL,
                $COL_VERSION INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ANALYSIS")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ANALYSIS")
        onCreate(db)
    }

    fun saveAnalysis(hash: String, bpm: Float, firstBeat: Long, waveform: ByteArray, version: Int, altKey: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HASH, hash)
            put(COL_BPM, bpm)
            put(COL_FIRST_BEAT, firstBeat)
            put(COL_WAVEFORM, waveform)
            put(COL_VERSION, version)
        }
        db.insertWithOnConflict(TABLE_ANALYSIS, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (!altKey.isNullOrBlank() && altKey != hash) {
            val altValues = ContentValues().apply {
                put(COL_HASH, altKey)
                put(COL_BPM, bpm)
                put(COL_FIRST_BEAT, firstBeat)
                put(COL_WAVEFORM, waveform)
                put(COL_VERSION, version)
            }
            db.insertWithOnConflict(TABLE_ANALYSIS, null, altValues, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun getAnalysis(key: String, altKey: String? = null): AnalysisResult? {
        val db = readableDatabase
        val selection = if (!altKey.isNullOrBlank() && altKey != key) {
            "$COL_HASH = ? OR $COL_HASH = ?"
        } else {
            "$COL_HASH = ?"
        }
        val selectionArgs = if (!altKey.isNullOrBlank() && altKey != key) {
            arrayOf(key, altKey)
        } else {
            arrayOf(key)
        }

        val cursor = db.query(
            TABLE_ANALYSIS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        return if (cursor.moveToFirst()) {
            val result = AnalysisResult(
                bpm = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_BPM)),
                firstBeatFrame = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FIRST_BEAT)),
                waveform = cursor.getBlob(cursor.getColumnIndexOrThrow(COL_WAVEFORM)),
                version = cursor.getInt(cursor.getColumnIndexOrThrow(COL_VERSION))
            )
            cursor.close()
            result
        } else {
            cursor.close()
            null
        }
    }
}

data class AnalysisResult(
    val bpm: Float,
    val firstBeatFrame: Long,
    val waveform: ByteArray,
    val version: Int
)
