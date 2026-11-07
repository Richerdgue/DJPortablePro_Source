package com.djportablepro.app.kymatik.domain

/**
 * Professional BeatGrid Data Structure for Kymatik Pro DJ Engine.
 */
data class KymatikBeatGrid(
    val bpm: Double,
    val firstBeatFrame: Long,
    val sampleRate: Int = 44100
) {
    val framesPerBeat: Double
        get() = (60.0 / bpm) * sampleRate

    val msPerBeat: Double
        get() = (60.0 / bpm) * 1000.0

    fun getBeatFrame(beatIndex: Long): Long {
        return firstBeatFrame + (beatIndex * framesPerBeat).toLong()
    }
}
