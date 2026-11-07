package com.djportablepro.app.library

import android.content.Context
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import com.djportablepro.app.analysis.BpmAnalysisService
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

/**
 * Orchestrates Raw C++ DSP Track Analysis with Bayesian Reference Guidance.
 * Passes soft reference hints (from filename patterns) to C++ NDK to eliminate octave errors.
 */
class TrackAnalysisManager(
    private val context: Context,
    private val audioPlayer: NativeAudioPlayer
) {
    private val db = TrackDatabase(context)
    private val CURRENT_ANALYZER_VERSION = 5 // Version 5: Polyrhythmic Ratio 7/9 Un-folding (98.34 -> 126.5 BPM)
    private val bpmAnalysisService = BpmAnalysisService()

    suspend fun getOrAnalyze(path: String, title: String = "", forceReanalyze: Boolean = false): AnalysisResult? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null

        val file = File(path)
        val isContentUri = path.startsWith("content://")

        if (!isContentUri && !file.exists()) {
            return@withContext null
        }

        val fastKey = generateFastKey(file, path)

        // Check cache with both fastKey and exact path (skipped if forceReanalyze is true)
        if (!forceReanalyze) {
            val cached = db.getAnalysis(fastKey, path)
            if (cached != null && cached.version == CURRENT_ANALYZER_VERSION && cached.bpm > 0f) {
                return@withContext cached
            }
        }

        // Set Background Thread Priority so DSP analysis yields CPU cores to audio & UI Choreographer
        val tid = Process.myTid()
        val oldPriority = try { Process.getThreadPriority(tid) } catch (_: Throwable) { 0 }
        try {
            Process.setThreadPriority(tid, Process.THREAD_PRIORITY_BACKGROUND + 5)
        } catch (_: Throwable) {}

        try {
            yield()

            // Extract EXPLICIT 'bpm' tag reference hint (e.g. '126.6bpm') to guide C++ NDK fine scan
            val refHint = bpmAnalysisService.extractExplicitBpmHint(path).toFloat()

            // Run C++ Audio DSP Analysis
            val resolvedPath = resolveAudioPathOrFd(context, path)
            val rawResult = audioPlayer.analyzeTrack(resolvedPath, refHint) ?: return@withContext null

            val finalBpm = if (rawResult.bpm > 50.0f) {
                bpmAnalysisService.snapBpm(rawResult.bpm.toDouble()).toFloat()
            } else if (refHint > 50.0f) {
                refHint
            } else {
                128.0f
            }

            val finalResult = rawResult.copy(bpm = finalBpm)

            if (finalResult.bpm > 0f) {
                // Save analysis under BOTH fastKey AND path so Library UI and Manager both find it
                db.saveAnalysis(
                    hash = fastKey,
                    bpm = finalResult.bpm,
                    firstBeat = finalResult.firstBeatFrame,
                    waveform = finalResult.waveform,
                    version = finalResult.version,
                    altKey = path
                )
            }

            finalResult
        } finally {
            try { Process.setThreadPriority(tid, oldPriority) } catch (_: Throwable) {}
        }
    }

    private fun resolveAudioPathOrFd(context: Context, path: String): String {
        if (!path.startsWith("content://")) return path

        try {
            val uri = Uri.parse(path)
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataIndex != -1) {
                        val realPath = cursor.getString(dataIndex)
                        if (!realPath.isNullOrBlank() && File(realPath).exists()) {
                            return realPath
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
            if (pfd != null) {
                val fd = pfd.detachFd()
                return "fd:$fd"
            }
        } catch (_: Exception) {}

        return path
    }

    private fun generateFastKey(file: File, path: String): String {
        return try {
            val length = if (file.exists()) file.length() else path.length.toLong()
            val lastModified = if (file.exists()) file.lastModified() else 0L
            "${path.hashCode()}_${length}_${lastModified}"
        } catch (_: Exception) {
            path.hashCode().toString()
        }
    }
}
