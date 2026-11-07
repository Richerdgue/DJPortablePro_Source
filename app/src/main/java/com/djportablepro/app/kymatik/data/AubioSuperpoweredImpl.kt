package com.djportablepro.app.kymatik.data

import com.djportablepro.app.kymatik.domain.KymatikBpmAnalyzer
import com.djportablepro.app.kymatik.domain.KymatikResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native JNI Implementation of KymatikBpmAnalyzer binding C++ Aubio / Superpowered Engine.
 */
class AubioSuperpoweredImpl : KymatikBpmAnalyzer {

    companion object {
        init {
            try {
                System.loadLibrary("djportablepro")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private external fun nAnalyzeTrack(filePath: String, referenceBpmHint: Double): KymatikResult?

    override suspend fun analyzeTrack(filePath: String, referenceBpmHint: Double): KymatikResult {
        return withContext(Dispatchers.IO) {
            try {
                val nativeRes = nAnalyzeTrack(filePath, referenceBpmHint)
                nativeRes ?: KymatikResult(
                    bpm = 128.0,
                    firstBeatFrame = 0L,
                    confidence = 0.5,
                    key = "8A",
                    waveform = ByteArray(0)
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                KymatikResult(
                    bpm = 128.0,
                    firstBeatFrame = 0L,
                    confidence = 0.0,
                    key = "8A",
                    waveform = ByteArray(0)
                )
            }
        }
    }
}
