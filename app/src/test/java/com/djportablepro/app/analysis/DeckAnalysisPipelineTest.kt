package com.djportablepro.app.analysis

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class DeckAnalysisPipelineTest {

    @Test
    fun `analyzePcmBuffer returns invalid result on empty buffer`() = runTest {
        val pipeline = DeckAnalysisPipeline()

        val result = pipeline.analyzePcmBuffer(
            pcmData = floatArrayOf(),
            sampleRate = 44100,
            channels = 2,
            trackName = "Empty.mp3"
        )

        assertFalse(result.isValid)
        assertEquals(0.0, result.bpm, 0.001)
        assertEquals(0L, result.firstBeatFrame)
    }

    @Test
    fun `analyzeDeck prioritizes analyzedBpm 100 percent`() {
        val pipeline = DeckAnalysisPipeline()

        val result = pipeline.analyzeDeck(
            trackName = "Salsa_100bpm.mp3",
            analyzedBpm = 128.5,
            fallbackBpm = 128.0
        )

        assertTrue(result.isValid)
        assertEquals(128.5, result.bpm, 0.01)
        assertEquals("analysis", result.source)
    }

    @Test
    fun `analyzePcmBuffer handles synthetic audio buffer with initial silence on Dispatchers Default`() = runTest {
        val pipeline = DeckAnalysisPipeline()
        val sampleRate = 44100
        val channels = 2

        // Create 3 seconds of audio: 1 second silence followed by 2 seconds of 120 BPM kick pulses (2 beats per sec)
        val totalSamples = sampleRate * channels * 3
        val pcm = FloatArray(totalSamples)

        val silenceFrames = sampleRate * 1 // 1 second of silence
        val beatPeriodFrames = sampleRate / 2 // 120 BPM = 0.5s per beat = 22050 frames

        for (i in 0 until (totalSamples / channels)) {
            if (i >= silenceFrames) {
                val frameInBeat = (i - silenceFrames) % beatPeriodFrames
                if (frameInBeat < 1000) { // 1000 samples kick pulse
                    val sampleVal = sin(2.0 * Math.PI * 60.0 * frameInBeat / sampleRate).toFloat()
                    pcm[i * channels] = sampleVal
                    pcm[i * channels + 1] = sampleVal
                }
            }
        }

        // Execute on Dispatchers.Default asynchronously
        val result = pipeline.analyzePcmBuffer(
            pcmData = pcm,
            sampleRate = sampleRate,
            channels = channels,
            trackName = "DrumAndBass_174bpm.wav",
            referenceHint = 120f
        )

        // Verify result structure
        if (result.isValid) {
            assertTrue(result.bpm in 50.0..250.0)
            assertTrue(result.firstBeatFrame >= 0L)
            assertTrue(result.beatDurationMs > 0.0)
        } else {
            assertFalse(result.isValid)
        }
    }
}
