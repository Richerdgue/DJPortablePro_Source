package com.djportablepro.app.kymatik.service

import android.content.Context
import com.djportablepro.app.analysis.BpmAnalysisService
import com.djportablepro.app.kymatik.data.AubioSuperpoweredImpl
import com.djportablepro.app.kymatik.data.KymatikRepository
import com.djportablepro.app.kymatik.domain.KymatikResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clean Asynchronous Processing Pipeline for Kymatik Audio Analysis.
 */
class KymatikPipeline(context: Context) {
    private val repository = KymatikRepository(context)
    private val nativeEngine = AubioSuperpoweredImpl()
    private val hintService = BpmAnalysisService()

    suspend fun processTrack(filePath: String, forceReanalyze: Boolean = false): KymatikResult {
        return withContext(Dispatchers.Default) {
            if (!forceReanalyze) {
                val cached = repository.getCachedAnalysis(filePath)
                if (cached != null && cached.bpm > 0.0) {
                    return@withContext cached
                }
            }

            val bpmHint = hintService.extractBpmHint(filePath)
            val nativeResult = nativeEngine.analyzeTrack(filePath, bpmHint)

            val finalBpm = if (nativeResult.bpm > 50.0) {
                hintService.snapBpm(nativeResult.bpm)
            } else if (bpmHint > 50.0) {
                hintService.snapBpm(bpmHint)
            } else {
                128.0
            }

            val finalResult = nativeResult.copy(bpm = finalBpm)
            repository.saveAnalysis(filePath, finalResult)

            finalResult
        }
    }
}
