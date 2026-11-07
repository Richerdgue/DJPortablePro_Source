package com.djportablepro.app.audio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.djportablepro.app.library.AnalysisResult
import com.djportablepro.app.logging.DjLogger

/**
 * Kotlin bridge for the NDK Audio Engine and Deck Player.
 * Supports Deck A (0) and Deck B (1).
 */
class NativeAudioPlayer(private val context: Context) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    companion object {
        init {
            try {
                System.loadLibrary("djportablepro")
            } catch (e: Throwable) {
                DjLogger.e("NativeAudioPlayer", "Native audio library not found: ${e.message}")
            }
        }
        private const val TAG = "NativeAudioPlayer"
        
        const val DECK_A = 0
        const val DECK_B = 1
    }

    fun initialize(tier: Int): Boolean {
        DjLogger.i(TAG, "[ENGINE_INIT] Initializing NativeAudioPlayer with Tier: $tier")
        return try { nInitialize(tier) } catch (e: Throwable) { DjLogger.e(TAG, "nInitialize failed: ${e.message}"); false }
    }

    fun start(): Boolean {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            DjLogger.i(TAG, "[STREAM_VOLUME] STREAM_MUSIC volume: $currentVol / $maxVol")
            if (currentVol < (maxVol * 0.7f).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.8f).toInt(), 0)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        if (!requestAudioFocus()) {
            DjLogger.w(TAG, "[ENGINE_START] Failed to start: Audio Focus denied")
            return false
        }
        DjLogger.i(TAG, "[ENGINE_START] Starting Audio Engine")
        return try { nStart() } catch (e: Throwable) { DjLogger.e(TAG, "nStart failed: ${e.message}"); false }
    }

    fun stop(): Boolean {
        DjLogger.i(TAG, "[ENGINE_STOP] Stopping Audio Engine")
        abandonAudioFocus()
        return try { nStop() } catch (e: Throwable) { DjLogger.e(TAG, "nStop failed: ${e.message}"); false }
    }

    fun release() {
        DjLogger.i(TAG, "[ENGINE_RELEASE] Releasing NativeAudioPlayer")
        try { nRelease() } catch (e: Throwable) { DjLogger.e(TAG, "nRelease failed: ${e.message}") }
    }

    fun isInitialized(): Boolean = try { nIsInitialized() } catch (e: Throwable) { DjLogger.e(TAG, "nIsInitialized failed: ${e.message}"); false }

    // --- Deck Controls ---

    fun load(deckIndex: Int, path: String): Boolean {
        DjLogger.i(TAG, "[DECK_LOAD] Deck $deckIndex, Path: $path")
        return try { nLoad(deckIndex, path) } catch (e: Throwable) { DjLogger.e(TAG, "nLoad failed: ${e.message}"); false }
    }

    fun play(deckIndex: Int) {
        DjLogger.i(TAG, "[DECK_PLAY] Deck $deckIndex Playing")
        try { nPlay(deckIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nPlay failed: ${e.message}") }
    }

    fun pause(deckIndex: Int) {
        DjLogger.i(TAG, "[DECK_PAUSE] Deck $deckIndex Paused")
        try { nPause(deckIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nPause failed: ${e.message}") }
    }

    fun seek(deckIndex: Int, frameIndex: Long) {
        DjLogger.i(TAG, "[DECK_SEEK] Deck $deckIndex Frame: $frameIndex")
        try { nSeek(deckIndex, frameIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nSeek failed: ${e.message}") }
    }

    fun getPosition(deckIndex: Int): Long = try { nGetPosition(deckIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nGetPosition failed: ${e.message}"); 0L }
    fun getDuration(deckIndex: Int): Long = try { nGetDuration(deckIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nGetDuration failed: ${e.message}"); 0L }

    fun setPlaybackRate(deckIndex: Int, rate: Float) {
        try { nSetPlaybackRate(deckIndex, rate) } catch (e: Throwable) { DjLogger.e(TAG, "nSetPlaybackRate failed: ${e.message}") }
    }

    fun setDspParams(deckIndex: Int, low: Float, mid: Float, high: Float, filter: Float, gain: Float) {
        try { nSetDspParams(deckIndex, low, mid, high, filter, gain) } catch (e: Throwable) { DjLogger.e(TAG, "nSetDspParams failed: ${e.message}") }
    }

    // --- PRO DJ EFFECTS & ISOLATOR EQ ---

    fun setEqGains(deckIndex: Int, low: Float, mid: Float, high: Float) {
        try { nSetEqGains(deckIndex, low, mid, high) } catch (e: Throwable) { DjLogger.e(TAG, "nSetEqGains failed: ${e.message}") }
    }

    fun setEqKills(deckIndex: Int, killLow: Boolean, killMid: Boolean, killHigh: Boolean) {
        try { nSetEqKills(deckIndex, killLow, killMid, killHigh) } catch (e: Throwable) { DjLogger.e(TAG, "nSetEqKills failed: ${e.message}") }
    }

    fun setDelayParams(deckIndex: Int, beatFraction: Float, feedback: Float, wet: Float, freeze: Boolean = false) {
        try { nSetDelayParams(deckIndex, beatFraction, feedback, wet, freeze) } catch (e: Throwable) { DjLogger.e(TAG, "nSetDelayParams failed: ${e.message}") }
    }

    fun setReverbParams(deckIndex: Int, roomSize: Float, wet: Float) {
        try { nSetReverbParams(deckIndex, roomSize, wet) } catch (e: Throwable) { DjLogger.e(TAG, "nSetReverbParams failed: ${e.message}") }
    }

    fun setBitcrusherParams(deckIndex: Int, factor: Float, wet: Float) {
        try { nSetBitcrusherParams(deckIndex, factor, wet) } catch (e: Throwable) { DjLogger.e(TAG, "nSetBitcrusherParams failed: ${e.message}") }
    }

    fun setBeatRollParams(deckIndex: Int, fraction: Float, active: Boolean) {
        try { nSetBeatRollParams(deckIndex, fraction, active) } catch (e: Throwable) { DjLogger.e(TAG, "nSetBeatRollParams failed: ${e.message}") }
    }

    fun setVolume(deckIndex: Int, volume: Float) {
        try { nSetVolume(deckIndex, volume) } catch (e: Throwable) { DjLogger.e(TAG, "nSetVolume failed: ${e.message}") }
    }

    fun setScratchMode(deckIndex: Int, enabled: Boolean) {
        try { nSetScratchMode(deckIndex, enabled) } catch (e: Throwable) { DjLogger.e(TAG, "nSetScratchMode failed: ${e.message}") }
    }

    fun setScratchVelocity(deckIndex: Int, velocity: Float) {
        try { nSetScratchVelocity(deckIndex, velocity) } catch (e: Throwable) { DjLogger.e(TAG, "nSetScratchVelocity failed: ${e.message}") }
    }

    fun setCrossfader(position: Float) {
        try { nSetCrossfader(position) } catch (e: Throwable) { DjLogger.e(TAG, "nSetCrossfader failed: ${e.message}") }
    }

    fun setBeatGrid(deckIndex: Int, bpm: Float, firstBeatFrame: Long) {
        try { nSetBeatGrid(deckIndex, bpm, firstBeatFrame) } catch (e: Throwable) { DjLogger.e(TAG, "nSetBeatGrid failed: ${e.message}") }
    }

    fun setDownbeatPhase(deckIndex: Int, phase: Double) {
        try { nSetDownbeatPhase(deckIndex, phase) } catch (e: Throwable) { DjLogger.e(TAG, "nSetDownbeatPhase failed: ${e.message}") }
    }

    fun getDownbeatPhase(deckIndex: Int): Double = try { nGetDownbeatPhase(deckIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nGetDownbeatPhase failed: ${e.message}"); 0.0 }

    fun setLoop(deckIndex: Int, startFrame: Long, endFrame: Long, enabled: Boolean) {
        try { nSetLoop(deckIndex, startFrame, endFrame, enabled) } catch (e: Throwable) { DjLogger.e(TAG, "nSetLoop failed: ${e.message}") }
    }

    fun getBeatPhase(deckIndex: Int): Double = try { nGetBeatPhase(deckIndex) } catch (e: Throwable) { DjLogger.e(TAG, "nGetBeatPhase failed: ${e.message}"); 0.0 }

    fun alignPhase(deckIndex: Int, targetPhase: Double) {
        try { nAlignPhase(deckIndex, targetPhase) } catch (e: Throwable) { DjLogger.e(TAG, "nAlignPhase failed: ${e.message}") }
    }

    fun setSyncActive(deckIndex: Int, active: Boolean) {
        try { nSetSyncActive(deckIndex, active) } catch (e: Throwable) { DjLogger.e(TAG, "nSetSyncActive failed: ${e.message}") }
    }

    data class SyncAdjustment(
        val pitchRatio: Float,
        val microNudgeFactor: Float,
        val finalPlaybackRate: Float,
        val sampleOffsetCorrection: Long,
        val phaseErrorMs: Float,
        val isLowEndInPhase: Boolean
    )

    fun calculateSyncAdjustment(slaveDeckIndex: Int, slaveBaseBpm: Double, slaveFirstBeatFrame: Long, isSyncTriggered: Boolean = false): SyncAdjustment? {
        val arr = try { nCalculateSyncAdjustment(slaveDeckIndex, slaveBaseBpm, slaveFirstBeatFrame, isSyncTriggered) } catch (e: Throwable) { null }
        if (arr == null || arr.size < 6) return null
        return SyncAdjustment(
            pitchRatio = arr[0],
            microNudgeFactor = arr[1],
            finalPlaybackRate = arr[2],
            sampleOffsetCorrection = arr[3].toLong(),
            phaseErrorMs = arr[4],
            isLowEndInPhase = arr[5] > 0.5f
        )
    }

    // --- Analysis ---
    
    fun analyzeTrack(path: String, referenceBpm: Float = 0.0f): AnalysisResult? {
        DjLogger.i(TAG, "[ANALYZER] Requesting analysis for $path (RefHint: $referenceBpm)")
        return try { nAnalyzeTrack(path, referenceBpm) } catch (e: Throwable) { DjLogger.e(TAG, "nAnalyzeTrack failed: ${e.message}"); null }
    }

    // --- Audio Focus Management ---

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this)
                .build()

            audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                DjLogger.d(TAG, "[AUDIO_FOCUS] Gained")
                if (isInitialized()) try { nStart() } catch (e: Throwable) { DjLogger.e(TAG, "nStart failed on focus gain: ${e.message}") }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                DjLogger.d(TAG, "[AUDIO_FOCUS] Loss (Permanent)")
                try { nStop() } catch (e: Throwable) { DjLogger.e(TAG, "nStop failed on focus loss: ${e.message}") }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                DjLogger.d(TAG, "[AUDIO_FOCUS] Loss (Transient)")
                try { nStop() } catch (e: Throwable) { DjLogger.e(TAG, "nStop failed on transient focus loss: ${e.message}") }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                DjLogger.d(TAG, "[AUDIO_FOCUS] Loss (Can Duck)")
            }
        }
    }

    // Native methods
    private external fun nInitialize(tier: Int): Boolean
    private external fun nStart(): Boolean
    private external fun nStop(): Boolean
    private external fun nRelease()
    private external fun nIsInitialized(): Boolean

    private external fun nLoad(deckIndex: Int, path: String): Boolean
    private external fun nPlay(deckIndex: Int)
    private external fun nPause(deckIndex: Int)
    private external fun nSeek(deckIndex: Int, frameIndex: Long)
    private external fun nGetPosition(deckIndex: Int): Long
    private external fun nGetDuration(deckIndex: Int): Long
    private external fun nSetPlaybackRate(deckIndex: Int, rate: Float)
    private external fun nSetDspParams(deckIndex: Int, low: Float, mid: Float, high: Float, filter: Float, gain: Float)
    private external fun nSetEqGains(deckIndex: Int, low: Float, mid: Float, high: Float)
    private external fun nSetEqKills(deckIndex: Int, killLow: Boolean, killMid: Boolean, killHigh: Boolean)
    private external fun nSetDelayParams(deckIndex: Int, beatFraction: Float, feedback: Float, wet: Float, freeze: Boolean)
    private external fun nSetReverbParams(deckIndex: Int, roomSize: Float, wet: Float)
    private external fun nSetBitcrusherParams(deckIndex: Int, factor: Float, wet: Float)
    private external fun nSetBeatRollParams(deckIndex: Int, fraction: Float, active: Boolean)
    private external fun nSetVolume(deckIndex: Int, volume: Float)
    private external fun nSetScratchMode(deckIndex: Int, enabled: Boolean)
    private external fun nSetScratchVelocity(deckIndex: Int, velocity: Float)
    private external fun nSetCrossfader(position: Float)
    private external fun nSetBeatGrid(deckIndex: Int, bpm: Float, firstBeatFrame: Long)
    private external fun nSetDownbeatPhase(deckIndex: Int, phase: Double)
    private external fun nSetLoop(deckIndex: Int, startFrame: Long, endFrame: Long, enabled: Boolean)
    private external fun nGetBeatPhase(deckIndex: Int): Double
    private external fun nGetDownbeatPhase(deckIndex: Int): Double
    private external fun nAlignPhase(deckIndex: Int, targetPhase: Double)
    private external fun nSetSyncActive(deckIndex: Int, active: Boolean)
    private external fun nCalculateSyncAdjustment(slaveDeckIndex: Int, slaveBaseBpm: Double, slaveFirstBeatFrame: Long, isSyncTriggered: Boolean): FloatArray?
    
    private external fun nAnalyzeTrack(path: String, referenceBpm: Float): AnalysisResult?
}
