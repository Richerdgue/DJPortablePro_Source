package com.djportablepro.app.audio.engine

import com.djportablepro.app.library.AnalysisResult

/**
 * Kotlin bridge for real-time C++ NDK BPM analysis.
 * Receives streaming PCM audio buffers and processes them asynchronously off the UI thread.
 */
class NativeBpmAnalyzer {

    private var nativeHandle: Long = 0

    init {
        try {
            System.loadLibrary("djportablepro")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Initializes the native DSP engine.
     */
    fun init(sampleRate: Int = 44100, channels: Int = 2) {
        if (nativeHandle == 0L) {
            nativeHandle = nInit(sampleRate, channels)
        }
    }

    /**
     * Pushes floating-point PCM samples into the native ring buffer queue.
     * High-throughput call designed to return in sub-milliseconds without blocking caller.
     */
    fun pushPcmSamples(samples: FloatArray) {
        if (nativeHandle != 0L && samples.isNotEmpty()) {
            nPushPcm(nativeHandle, samples, samples.size)
        }
    }

    /**
     * Retrieves the latest detected BPM from C++ engine.
     */
    fun getBpm(): Float {
        return if (nativeHandle != 0L) nGetBpm(nativeHandle) else 0.0f
    }

    /**
     * Retrieves the detection confidence metric (0.0 to 1.0).
     */
    fun getConfidence(): Float {
        return if (nativeHandle != 0L) nGetConfidence(nativeHandle) else 0.0f
    }

    /**
     * Resets the native DSP buffer queues and state.
     */
    fun reset() {
        if (nativeHandle != 0L) {
            nReset(nativeHandle)
        }
    }

    /**
     * Releases native C++ memory and stops worker thread.
     */
    fun release() {
        if (nativeHandle != 0L) {
            nRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    fun analyzeBufferAsync(
        pcmData: FloatArray,
        sampleRate: Int,
        channels: Int,
        referenceHint: Float
    ): AnalysisResult {
        return try {
            nAnalyzeBuffer(pcmData, pcmData.size, sampleRate, channels, referenceHint)
                ?: AnalysisResult(128.0f, 0L, ByteArray(0), 12)
        } catch (_: Throwable) {
            AnalysisResult(128.0f, 0L, ByteArray(0), 12)
        }
    }

    fun analyzeFileAsync(filePath: String, referenceHint: Float): AnalysisResult {
        return try {
            nAnalyzeFile(filePath, referenceHint)
                ?: AnalysisResult(128.0f, 0L, ByteArray(0), 12)
        } catch (_: Throwable) {
            AnalysisResult(128.0f, 0L, ByteArray(0), 12)
        }
    }

    // --- Native Methods ---
    private external fun nInit(sampleRate: Int, channels: Int): Long
    private external fun nPushPcm(handle: Long, samples: FloatArray, count: Int)
    private external fun nGetBpm(handle: Long): Float
    private external fun nGetConfidence(handle: Long): Float
    private external fun nReset(handle: Long)
    private external fun nRelease(handle: Long)

    private external fun nAnalyzeFile(path: String, referenceHint: Float): AnalysisResult?
    private external fun nAnalyzeBuffer(
        pcmArray: FloatArray,
        totalSamples: Int,
        sampleRate: Int,
        channels: Int,
        referenceHint: Float
    ): AnalysisResult?
}
