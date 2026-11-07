package com.djportablepro.app.kymatik.domain

/**
 * Domain Interface for Kymatik Audio Analysis Engine.
 */
interface KymatikBpmAnalyzer {
    suspend fun analyzeTrack(filePath: String, referenceBpmHint: Double = 0.0): KymatikResult
}
