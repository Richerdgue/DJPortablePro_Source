package com.djportablepro.app.kymatik.domain

/**
 * Unified Analysis Result for the Kymatik Engine (Aubio C++ / Superpowered DSP).
 */
data class KymatikResult(
    val bpm: Double,
    val firstBeatFrame: Long,
    val confidence: Double,
    val key: String,
    val waveform: ByteArray,
    val version: Int = 3
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KymatikResult

        if (bpm != other.bpm) return false
        if (firstBeatFrame != other.firstBeatFrame) return false
        if (confidence != other.confidence) return false
        if (key != other.key) return false
        if (!waveform.contentEquals(other.waveform)) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bpm.hashCode()
        result = 31 * result + firstBeatFrame.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + waveform.contentHashCode()
        result = 31 * result + version
        return result
    }
}
