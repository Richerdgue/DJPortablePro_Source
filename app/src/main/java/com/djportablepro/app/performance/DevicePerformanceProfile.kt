package com.djportablepro.app.performance

import android.app.ActivityManager
import android.content.Context

/**
 * Defines a safe runtime profile for low-memory Android devices.
 *
 * Strategy:
 * - 2GB+ devices are supported in reduced mode
 * - 4GB+ devices are supported in full mode
 * - low-memory devices reduce render rate, waveform precision, and analysis cost
 */
data class DevicePerformanceProfile(
    val totalRamMb: Long,
    val isLowRamDevice: Boolean,
    val tier: Int,
    val modeLabel: String,
    val audioEngineTier: Int,
    val renderMode: Int,
    val waveformSamples: Int,
    val enableHeavyAnalysis: Boolean,
    val enableContinuousRendering: Boolean,
) {
    val isCompatibleWithRecommendedTarget: Boolean
        get() = totalRamMb >= 2048L

    val isFullExperience: Boolean
        get() = totalRamMb >= 4096L
}

object DevicePerformanceProfileEvaluator {
    fun evaluate(context: Context): DevicePerformanceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        val lowRam = activityManager.isLowRamDevice || maxMemoryMb < 1536L

        val tier = when {
            maxMemoryMb < 1536L -> 0
            maxMemoryMb < 3072L -> 1
            maxMemoryMb < 6144L -> 2
            else -> 3
        }

        val modeLabel = when {
            maxMemoryMb < 2048L -> "Ahorro de RAM"
            maxMemoryMb < 4096L -> "Equilibrado"
            else -> "Completo"
        }

        val audioEngineTier = when {
            lowRam -> 0
            maxMemoryMb < 3072L -> 1
            maxMemoryMb < 6144L -> 2
            else -> 3
        }

        val enableContinuousRendering = !lowRam && maxMemoryMb >= 2048L
        val enableHeavyAnalysis = maxMemoryMb >= 4096L
        val waveformSamples = when {
            maxMemoryMb < 2048L -> 512
            maxMemoryMb < 4096L -> 1024
            else -> 2048
        }

        val renderMode = if (enableContinuousRendering) {
            1 // GLSurfaceView.RENDERMODE_CONTINUOUSLY
        } else {
            0 // GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        return DevicePerformanceProfile(
            totalRamMb = maxMemoryMb,
            isLowRamDevice = lowRam,
            tier = tier,
            modeLabel = modeLabel,
            audioEngineTier = audioEngineTier,
            renderMode = renderMode,
            waveformSamples = waveformSamples,
            enableHeavyAnalysis = enableHeavyAnalysis,
            enableContinuousRendering = enableContinuousRendering,
        )
    }
}
